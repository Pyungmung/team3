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
        아파트      건물명 aptNm    / 면적 excluUseAr   / floor / jibun 있음 / roadnm(도로명) 있음
        오피스텔    건물명 offiNm   / 면적 excluUseAr   / floor / jibun 있음 / roadnm 없음
        연립다세대  건물명 mhouseNm / 면적 excluUseAr   / floor / houseType(다세대·연립) / jibun 있음 / roadnm 없음
        단독다가구  건물명 없음     / 면적 totalFloorAr(연면적) / 층 없음 / houseType(단독·다가구) / jibun 없음

    주소 표시 정확도는 유형별로 다르다 - 아파트는 API 응답 자체에 도로명이 있어 바로 도로명주소를
    만들 수 있지만, 오피스텔/연립다세대는 지번만 있어 도로명이 필요하면 juso_api.py로 변환해야
    하고, 단독다가구는 지번조차 없어 법정동(umdNm) 단위가 한계다 (services/juso_api.py 참고).

    인증 오류 응답(JSON) 예시:
        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR", ...}}}

서비스키 발급: https://www.data.go.kr -> 각 API 활용신청(보통 즉시 승인) -> "일반 인증키(Decoding)"를
              customhouse-ai/.env의 DATA_GO_KR_API_KEY 에 넣는다. 4종 모두 같은 키를 쓴다.

성능: 진단 1건이 통근권 내 여러 지역 x 유형 4종 x 최근 2개월을 조회하므로 순차 호출하면 콜드
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
from app.services import juso_api, kakao_geocode

logger = logging.getLogger(__name__)

# 지금은 서울 25개 자치구만 후보 지역으로 쓰므로(app/data/regions.json 참고) 시/도명을 고정한다.
SIDO_NAME = "서울특별시"

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

# juso.go.kr(도로명주소/상세주소 API)은 data.go.kr(국토부)과 완전히 다른 서비스라 풀을 분리한다.
# 같은 풀을 썼다면 juso.go.kr 쪽 타임아웃(실측 5초 발생) 하나가 슬롯을 묶어서 국토부 호출까지
# 느려질 수 있다 - 두 서비스의 지연/장애가 서로에게 번지지 않도록 독립적으로 둔다.
_JUSO_POOL = ThreadPoolExecutor(max_workers=8, thread_name_prefix="juso")

# 카카오 로컬 API(주소->좌표)도 같은 이유로 juso.go.kr과 별도 풀을 쓴다 - 서로 다른 외부
# 서비스의 장애/지연이 섞이지 않게 한다 (juso.go.kr 156초 사건에서 얻은 교훈).
_GEOCODE_POOL = ThreadPoolExecutor(max_workers=8, thread_name_prefix="geocode")

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


def _road_address_from_item(item: dict, region_name: str) -> str | None:
    """아파트 응답에만 있는 roadnm으로 도로명주소를 바로 조립한다. roadnm은 "도로명 + 본번(-부번)"까지
    이미 합쳐진 문자열로 온다(예: "삼성로64길 5", "도산대로54길 48-8") - roadnmbonbun/roadnmbubun은
    같은 값을 따로 또 주는 필드라 표시에는 안 쓴다(중복 표기 방지). 별도 API 호출도 필요 없다."""
    road_name = item.get("roadnm", "")
    if not road_name:
        return None
    return f"{SIDO_NAME} {region_name} {road_name}"


def _normalize_building(item: dict, region_name: str, property_type: str) -> dict | None:
    """실거래 item 하나를 calculator.py가 쓰는 '건물(매물)' 카드 형태로 정규화한다.
    필수 필드(보증금/월세)가 없으면 None."""
    cfg = PROPERTY_TYPES[property_type]
    deposit = _parse_amount(_pick(item, ["deposit", "보증금액", "보증금"]))
    monthly_rent = _parse_amount(_pick(item, ["monthlyRent", "월세금액", "월세"]))
    if deposit is None or monthly_rent is None:
        return None

    dong = item.get("umdNm", "")
    jibun = item.get("jibun", "")
    house_type = item.get("houseType", "")

    building_name = item.get(cfg["name_field"], "") if cfg["name_field"] else ""
    unnamed = not building_name
    if unnamed:
        # 단독다가구는 건물명이 없고, 연립다세대도 간혹 비어있다 - 동네 + 건물 형태로 대신한다.
        building_name = f"{dong} {house_type or property_type}".strip()

    deal_year = item.get("dealYear", "")
    deal_month = item.get("dealMonth", "")

    # 주소 표시용: 아파트는 API 응답 자체 도로명으로 바로 완성되고(road_address), 나머지 유형은
    # 지번까지만 있어(jibun_address) juso_api.py로 변환하기 전까지의 폴백으로 쓴다
    # (단독다가구는 jibun조차 없어 둘 다 빈 문자열 - 동 단위 표시가 한계, response_schema.py 참고).
    road_address = _road_address_from_item(item, region_name)
    jibun_address = f"{SIDO_NAME} {region_name} {dong} {jibun}".strip() if dong and jibun else ""

    return {
        "region": region_name,
        "property_type": property_type,
        "building_name": building_name,
        "unnamed": unnamed,  # 건물명이 없어 동네+형태로 대신한 매물 - 중복 제거 시 가격/면적까지 구분해야 함
        "dong": dong,
        "jibun_search_keyword": jibun_address,  # juso_api.py 도로명 변환 검색어로 재사용 (배치 해석용)
        "road_address": road_address,
        "jibun_address": jibun_address,
        "deposit": round(deposit),
        "monthly_rent": round(monthly_rent),
        "lease_type": "전세" if round(monthly_rent) == 0 else "월세",
        "exclusive_area": _parse_amount(item.get(cfg["area_field"])),
        "floor": item.get("floor", "") if cfg["has_floor"] else "",
        "deal_date": f"{deal_year}.{deal_month}" if deal_year and deal_month else "",
    }


def _resolve_road_addresses(buildings: list[dict]) -> None:
    """도로명이 아직 없는(오피스텔/연립다세대) 매물들의 jibun_address를 juso_api로 변환해
    road_address를 채운다. 같은 건물(같은 지번)이 여러 실거래 건에 반복 등장하므로 지번 문자열
    기준으로 한 번만 조회하고 결과를 나머지에 그대로 복사한다. 매물 목록을 in-place로 수정한다."""
    if not juso_api.is_enabled():
        return

    needs_lookup = [b for b in buildings if not b["road_address"] and b["jibun_search_keyword"]]
    unique_keywords = {b["jibun_search_keyword"] for b in needs_lookup}
    if not unique_keywords:
        return

    def _lookup(keyword: str) -> tuple[str, str | None]:
        try:
            return keyword, juso_api.resolve_road_address(keyword)
        except juso_api.JusoApiError as e:
            logger.warning(str(e))
            return keyword, None

    resolved = dict(_JUSO_POOL.map(_lookup, unique_keywords))
    for building in needs_lookup:
        road_address = resolved.get(building["jibun_search_keyword"])
        if road_address:
            building["road_address"] = road_address


def _resolve_missing_building_names(buildings: list[dict]) -> None:
    """건물명이 없는 매물(연립다세대 등, unnamed=True)의 공식 대표 건물명을 도로명주소 검색 API
    응답(bdNm)으로 보완한다. 단독다가구처럼 애초에 지번이 없는 매물은 검색어 자체가 없어 대상에서
    제외된다."""
    if not juso_api.is_enabled():
        return

    needs_lookup = [b for b in buildings if b["unnamed"] and b["jibun_search_keyword"]]
    unique_keywords = {b["jibun_search_keyword"] for b in needs_lookup}
    if not unique_keywords:
        return

    def _lookup(keyword: str) -> tuple[str, str | None]:
        try:
            return keyword, juso_api.resolve_building_name(keyword)
        except juso_api.JusoApiError as e:
            logger.warning(str(e))
            return keyword, None

    resolved = dict(_JUSO_POOL.map(_lookup, unique_keywords))
    for building in needs_lookup:
        building_name = resolved.get(building["jibun_search_keyword"])
        if building_name:
            building["building_name"] = building_name
            building["unnamed"] = False


def _resolve_unambiguous_dongs(buildings: list[dict]) -> None:
    """도로명/지번주소가 잡힌 매물 중, 그 번지에 동(棟)이 정확히 1개뿐인 경우에만 상세주소 API로
    동명을 확인해서 주소 끝에 덧붙인다 (예: "화양로2길 10" -> "화양로2길 10 (A동)"). 동이 여러 개인
    복합건물은 실거래 1건이 그중 어느 동인지 알 수 없어 모호하므로 건드리지 않는다 - juso_api.py의
    resolve_unambiguous_dong 참고."""
    if not juso_api.is_detail_enabled():
        return

    needs_lookup = [b for b in buildings if (b["road_address"] or b["jibun_address"]) and b["jibun_search_keyword"]]
    unique_keywords = {b["jibun_search_keyword"] for b in needs_lookup}
    if not unique_keywords:
        return

    def _lookup(keyword: str) -> tuple[str, str | None]:
        try:
            return keyword, juso_api.resolve_unambiguous_dong(keyword)
        except juso_api.JusoApiError as e:
            logger.warning(str(e))
            return keyword, None

    resolved = dict(_JUSO_POOL.map(_lookup, unique_keywords))
    for building in needs_lookup:
        dong_name = resolved.get(building["jibun_search_keyword"])
        if not dong_name:
            continue
        if building["road_address"]:
            building["road_address"] = f"{building['road_address']} ({dong_name})"
        if building["jibun_address"]:
            building["jibun_address"] = f"{building['jibun_address']} ({dong_name})"


def fetch_candidate_buildings(lawd_cds: tuple[str, ...], region_name: str) -> list[dict]:
    """지역(법정동코드 튜플)의 최근 실거래 내역을 4개 유형 통틀어 개별 '건물(매물)' 후보 리스트로
    반환한다. calculator.py가 이 중에서 예산에 맞는 후보를 골라 추천한다.

    전세(월세 0원)와 월세 거래 모두 포함한다 - calculator.py가 lease_type("전세"/"월세")으로
    갈라서 각각 따로 추천 목록을 만든다.

    주의: 여기서는 주소(juso_api.py) 변환을 하지 않는다 - 한 지역의 원본 실거래 후보가 최대
    수천 건(4유형 x 2개월 x 최대 300건)이라, 다 나가기 전에 걸러지고 남는 소수의 최종 추천
    건만 주소를 붙이는 게 훨씬 효율적이다 (resolve_addresses 참고, calculator.py가 예산 필터링
    +top_n 정리까지 끝낸 뒤 호출한다). 2026-09-28: 원본 단계에서 다 붙이려다 통근범위가 넓은
    진단 1건이 juso.go.kr에 수천 건을 동시에 쏴서 156초까지 걸린 적이 있다.
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


def resolve_addresses(buildings: list[dict]) -> None:
    """calculator.py가 예산 필터링/정렬/top_n까지 끝낸 "최종 추천 목록"에만 호출한다 (원본
    후보 전체에 호출하면 안 됨 - fetch_candidate_buildings 주의사항 참고). road_address/
    jibun_address/jibun_search_keyword가 있는 dict 목록을 in-place로 수정해서 최종 표시용
    "address" 필드를 채운다."""
    _resolve_road_addresses(buildings)
    _resolve_missing_building_names(buildings)
    _resolve_unambiguous_dongs(buildings)
    for building in buildings:
        # 표시용 최종 주소: 도로명 우선, 없으면 지번, 그마저 없으면(단독다가구) 빈 문자열
        # (프론트에서 "구+동" 수준으로 대체 표시 - report-result.html 참고).
        building["address"] = building["road_address"] or building["jibun_address"]


def resolve_coordinates(buildings: list[dict]) -> None:
    """resolve_addresses로 "address"가 채워진 뒤에 호출한다. 주소 문자열을 카카오 로컬 API로
    위경도로 바꿔서 lat/lon을 채운다 - map-util.js가 이 좌표로 매물별 정확한 위치에 핀을 찍는다.
    address가 없는 매물(단독다가구 등)은 애초에 지오코딩할 문자열이 없어 lat/lon이 None으로
    남고, 프론트가 지역 대표 좌표로 대체 표시한다. 같은 이유(juso.go.kr 과부하 156초 사건)로
    이 함수도 반드시 "최종 추천 목록"에만 호출해야 한다 - fetch_candidate_buildings 주의사항 참고."""
    if not kakao_geocode.is_enabled():
        return

    needs_lookup = [b for b in buildings if b.get("address")]
    unique_addresses = {b["address"] for b in needs_lookup}
    if not unique_addresses:
        return

    def _lookup(address: str) -> tuple[str, tuple[float, float] | None]:
        try:
            return address, kakao_geocode.geocode_address(address)
        except kakao_geocode.KakaoGeocodeError as e:
            logger.warning(str(e))
            return address, None

    resolved = dict(_GEOCODE_POOL.map(_lookup, unique_addresses))
    for building in needs_lookup:
        coord = resolved.get(building["address"])
        building["lat"], building["lon"] = coord if coord else (None, None)
