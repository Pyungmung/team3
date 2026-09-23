"""
[담당: 송귀성] 공공 API 수집기 - 국토교통부 전월세 실거래가 (아파트/오피스텔/연립다세대/단독다가구)

data.go.kr(공공데이터포털)의 국토교통부 전월세 실거래가 API 4종을 호출해서
지역(법정동코드) x 월(YYYYMM) 단위 실거래 내역을 개별 "매물" 후보로 정규화한다.
calculator.py가 이 후보 중에서 예산에 맞는 것을 골라 추천한다.

    GET https://apis.data.go.kr/1613000/{RTMSDataSvcAptRent|RTMSDataSvcOffiRent|
                                        RTMSDataSvcRHRent|RTMSDataSvcSHRent}/getXXX
        ?serviceKey=...&LAWD_CD=11680&DEAL_YMD=202608&numOfRows=300

    ⚠️ &type=json 파라미터는 이 API에서 무시된다 - 정상 응답은 항상 XML로 온다.
       (인증/게이트웨이 오류 응답만 JSON으로 온다. 아래 _parse_response가 둘 다 처리한다.)

    유형별 응답 필드 (2026-09 실측, 공통: deposit/monthlyRent는 "만원" 단위 콤마 문자열,
    monthlyRent가 0이면 전세 거래, umdNm/dealYear/dealMonth/dealDay):
        아파트      건물명 aptNm    / 면적 excluUseAr   / floor
        오피스텔    건물명 offiNm   / 면적 excluUseAr   / floor
        연립다세대  건물명 mhouseNm / 면적 excluUseAr   / floor / houseType(다세대·연립)
        단독다가구  건물명 없음     / 면적 totalFloorAr(연면적) / 층 없음 / houseType(단독·다가구)

    인증 오류 응답(JSON) 예시:
        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR", ...}}}

서비스키 발급: https://www.data.go.kr -> 각 API 활용신청(보통 즉시 승인) -> "일반 인증키(Decoding)"를
              customhouse-ai/.env의 DATA_GO_KR_API_KEY 에 넣는다. 4종 모두 같은 키를 쓴다.

성능: 진단 1건이 통근권 내 여러 지역 x 유형 4종 x 최근 3개월을 조회하므로 순차 호출하면 콜드
스타트에 수십 초가 걸려 백엔드 타임아웃(502)이 났다. 그래서 개별 HTTP 호출은 공용 스레드풀로 동시에
보내고(_HTTP_POOL, data.go.kr 부하를 위해 동시 호출 수 제한), 실패한 호출이 하나라도 있는 지역 결과는
캐시하지 않는다 (예전 lru_cache는 타임아웃으로 빈 결과가 나와도 서버 재시작 전까지 영구 캐시했음).
"""
import json
import logging
import threading
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from datetime import date

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

MOLIT_BASE_URL = "https://apis.data.go.kr/1613000/"
REQUEST_TIMEOUT_SEC = 8
MONTHS_BACK = 2  # 실거래 신고는 계약 후 30일 이내라 당월은 비어있을 수 있어 최근 2개월을 모은다.

# 유형별 엔드포인트와 응답 필드 매핑. name_field가 None이면 건물명이 없는 유형(단독다가구).
PROPERTY_TYPES = {
    "아파트": {
        "endpoint": "RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
        "name_field": "aptNm",
        "area_field": "excluUseAr",
        "has_floor": True,
    },
    "오피스텔": {
        "endpoint": "RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent",
        "name_field": "offiNm",
        "area_field": "excluUseAr",
        "has_floor": True,
    },
    "연립다세대": {
        "endpoint": "RTMSDataSvcRHRent/getRTMSDataSvcRHRent",
        "name_field": "mhouseNm",
        "area_field": "excluUseAr",
        "has_floor": True,
    },
    "단독다가구": {
        "endpoint": "RTMSDataSvcSHRent/getRTMSDataSvcSHRent",
        "name_field": None,
        "area_field": "totalFloorAr",
        "has_floor": False,
    },
}

# 캐시: (법정동코드 튜플, 지역명) -> (저장 시각, 매물 리스트). 실거래 데이터는 하루에도 조금씩 늘어나서
# 영구 캐시 대신 TTL을 둔다.
CACHE_TTL_SEC = 6 * 60 * 60

# 개별 HTTP 호출용 공용 스레드풀. 여러 지역을 동시에 조회해도 data.go.kr로 나가는 동시 호출은
# 이 개수를 넘지 않는다 (지역 단위 병렬화는 calculator.py가 별도 풀로 하므로 데드락 없음).
_HTTP_POOL = ThreadPoolExecutor(max_workers=12, thread_name_prefix="molit")

_cache: dict[tuple, tuple[float, list[dict]]] = {}
_cache_lock = threading.Lock()


class MolitApiError(Exception):
    """국토교통부 API 호출/인증 실패 (호출부에서 잡아서 샘플 데이터로 폴백시킨다)."""


def is_enabled() -> bool:
    """DATA_GO_KR_API_KEY가 설정되어 있어야 실 API를 시도한다. 없으면 즉시 폴백."""
    return bool(settings.data_go_kr_api_key)


def _pick(item: dict, keys: list[str]):
    for key in keys:
        if key in item:
            return item[key]
    return None


def _parse_amount(raw) -> float | None:
    """API가 "124,000"처럼 콤마/공백이 섞인 문자열로 금액을 내려주므로 안전하게 파싱."""
    if raw is None:
        return None
    try:
        return float(str(raw).replace(",", "").strip())
    except ValueError:
        return None


def _parse_response(text: str, lawd_cd: str, deal_ymd: str) -> list[dict]:
    """
    정상 응답(XML)이면 item 목록을 dict 리스트로 반환하고,
    인증/게이트웨이 오류 응답(JSON)이면 MolitApiError를 던진다.
    """
    stripped = text.lstrip()

    if stripped.startswith("{"):
        try:
            body = json.loads(text)
        except ValueError as e:
            raise MolitApiError(f"알 수 없는 응답 형식(LAWD_CD={lawd_cd}, {deal_ymd}): {text[:200]}") from e

        header = body.get("OpenAPI_ServiceResponse", {}).get("cmmMsgHeader")
        if header:
            raise MolitApiError(f"국토교통부 API 인증/요청 오류: {header.get('errMsg')} - {header.get('returnAuthMsg')}")
        raise MolitApiError(f"알 수 없는 JSON 응답(LAWD_CD={lawd_cd}, {deal_ymd}): {text[:200]}")

    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        raise MolitApiError(f"XML 파싱 실패(LAWD_CD={lawd_cd}, {deal_ymd}): {e}") from e

    result_code = root.findtext("header/resultCode")
    if result_code != "000":
        result_msg = root.findtext("header/resultMsg")
        raise MolitApiError(f"국토교통부 API 오류(LAWD_CD={lawd_cd}, {deal_ymd}): {result_code} - {result_msg}")

    return [
        {child.tag: (child.text or "").strip() for child in item_el}
        for item_el in root.findall("body/items/item")
    ]


def _recent_deal_ymds(months_back: int = MONTHS_BACK) -> list[str]:
    today = date.today()
    ymds = []
    for offset in range(months_back):
        month = today.month - offset
        year = today.year
        while month <= 0:
            month += 12
            year -= 1
        ymds.append(f"{year}{month:02d}")
    return ymds


def _fetch_one(lawd_cd: str, property_type: str, deal_ymd: str) -> list[dict]:
    """(법정동코드, 유형, 월) 1건을 조회해서 raw item 목록으로 돌려준다. 실패 시 MolitApiError."""
    params = {
        "serviceKey": settings.data_go_kr_api_key,
        "LAWD_CD": lawd_cd,
        "DEAL_YMD": deal_ymd,
        "numOfRows": 300,
    }
    url = MOLIT_BASE_URL + PROPERTY_TYPES[property_type]["endpoint"]
    try:
        res = requests.get(url, params=params, timeout=REQUEST_TIMEOUT_SEC)
        res.raise_for_status()
    except requests.RequestException as e:
        raise MolitApiError(f"국토교통부 API 호출 실패 ({property_type}, LAWD_CD={lawd_cd}, {deal_ymd}): {e}") from e

    return _parse_response(res.text, lawd_cd, deal_ymd)


def _normalize_building(item: dict, region_name: str, property_type: str) -> dict | None:
    """실거래 item 하나를 calculator.py가 쓰는 '건물(매물)' 카드 형태로 정규화한다.
    필수 필드(보증금/월세)가 없으면 None."""
    cfg = PROPERTY_TYPES[property_type]
    deposit = _parse_amount(_pick(item, ["deposit", "보증금액", "보증금"]))
    monthly_rent = _parse_amount(_pick(item, ["monthlyRent", "월세금액", "월세"]))
    if deposit is None or monthly_rent is None:
        return None

    dong = item.get("umdNm", "")
    house_type = item.get("houseType", "")

    building_name = item.get(cfg["name_field"], "") if cfg["name_field"] else ""
    unnamed = not building_name
    if unnamed:
        # 단독다가구는 건물명이 없고, 연립다세대도 간혹 비어있다 - 동네 + 건물 형태로 대신한다.
        building_name = f"{dong} {house_type or property_type}".strip()

    deal_year = item.get("dealYear", "")
    deal_month = item.get("dealMonth", "")

    return {
        "region": region_name,
        "property_type": property_type,
        "building_name": building_name,
        "unnamed": unnamed,  # 건물명이 없어 동네+형태로 대신한 매물 - 중복 제거 시 가격/면적까지 구분해야 함
        "dong": dong,
        "deposit": round(deposit),
        "monthly_rent": round(monthly_rent),
        "lease_type": "전세" if round(monthly_rent) == 0 else "월세",
        "exclusive_area": _parse_amount(item.get(cfg["area_field"])),
        "floor": item.get("floor", "") if cfg["has_floor"] else "",
        "deal_date": f"{deal_year}.{deal_month}" if deal_year and deal_month else "",
    }


def fetch_candidate_buildings(lawd_cds: tuple[str, ...], region_name: str) -> list[dict]:
    """지역(법정동코드 튜플)의 최근 실거래 내역을 4개 유형 통틀어 개별 '건물(매물)' 후보 리스트로
    반환한다. calculator.py가 이 중에서 예산에 맞는 후보를 골라 추천한다.

    전세(월세 0원)와 월세 거래 모두 포함한다 - calculator.py가 lease_type("전세"/"월세")으로
    갈라서 각각 따로 추천 목록을 만든다.
    """
    cache_key = (lawd_cds, region_name)
    with _cache_lock:
        cached = _cache.get(cache_key)
    if cached and time.time() - cached[0] < CACHE_TTL_SEC:
        return cached[1]

    tasks = [
        (lawd_cd, property_type, deal_ymd)
        for lawd_cd in lawd_cds
        for property_type in PROPERTY_TYPES
        for deal_ymd in _recent_deal_ymds()
    ]
    futures = [(t, _HTTP_POOL.submit(_fetch_one, *t)) for t in tasks]

    buildings = []
    had_error = False
    for (_, property_type, _), future in futures:
        try:
            items = future.result()
        except MolitApiError as e:
            logger.warning(str(e))
            had_error = True
            continue

        for item in items:
            building = _normalize_building(item, region_name, property_type)
            if building:
                buildings.append(building)

    # 하나라도 실패한 지역은 캐시하지 않아서, 일시적인 타임아웃이 서버 재시작 전까지 남지 않는다.
    if not had_error:
        with _cache_lock:
            _cache[cache_key] = (time.time(), buildings)

    return buildings
