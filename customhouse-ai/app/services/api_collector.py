"""
[담당: 송귀성] 공공 API 수집기 - 국토교통부 전월세 실거래가 (아파트/오피스텔/연립다세대/단독다가구)

data.go.kr(공공데이터포털)의 국토교통부 전월세 실거래가 API 4종을 호출해서
지역(법정동코드) x 월(YYYYMM) 단위 실거래 내역을 raw item 목록으로 가져온다(_fetch_one).

    GET https://apis.data.go.kr/1613000/{RTMSDataSvcAptRent|RTMSDataSvcOffiRent|
                                        RTMSDataSvcRHRent|RTMSDataSvcSHRent}/getXXX
        ?serviceKey=...&LAWD_CD=11680&DEAL_YMD=202608&numOfRows=1000&pageNo=1  (totalCount만큼 페이지 반복, _fetch_one)

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

성능: 지역 x 유형 x 월 조합을 여러 개 조회할 때 순차 호출하면 느려지므로, 개별 HTTP 호출은
공용 스레드풀(_HTTP_POOL)로 동시에 보낸다(data.go.kr 부하를 위해 동시 호출 수 제한).
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
# has_jibun=False(단독다가구)는 응답 자체에 지번 필드가 없다는 뜻 - listing_reference.py가 이 값을 보고
# 지번 매칭(fetch_reference_transactions) 시도 자체를 건너뛴다(어차피 100% 실패라 24개월치를
# 헛조회하느라 느려지기만 한다, 2026-10-01 실측 9~10초).
PROPERTY_TYPES = {
    "아파트": {
        "endpoint": "RTMSDataSvcAptRent/getRTMSDataSvcAptRent",
        "name_field": "aptNm",
        "area_field": "excluUseAr",
        "has_floor": True,
        "has_jibun": True,
    },
    "오피스텔": {
        "endpoint": "RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent",
        "name_field": "offiNm",
        "area_field": "excluUseAr",
        "has_floor": True,
        "has_jibun": True,
    },
    "연립다세대": {
        "endpoint": "RTMSDataSvcRHRent/getRTMSDataSvcRHRent",
        "name_field": "mhouseNm",
        "area_field": "excluUseAr",
        "has_floor": True,
        "has_jibun": True,
    },
    "단독다가구": {
        "endpoint": "RTMSDataSvcSHRent/getRTMSDataSvcSHRent",
        "name_field": None,
        "area_field": "totalFloorAr",
        "has_floor": False,
        "has_jibun": False,
    },
}

# 캐시: 호출 조건 튜플 -> (저장 시각, 결과). 실거래 데이터는 하루에도 조금씩 늘어나서
# 영구 캐시 대신 TTL을 둔다.
CACHE_TTL_SEC = 6 * 60 * 60

# 개별 HTTP 호출용 공용 스레드풀. 여러 지역을 동시에 조회해도 data.go.kr로 나가는 동시 호출은
# 이 개수를 넘지 않는다.
_HTTP_POOL = ThreadPoolExecutor(max_workers=12, thread_name_prefix="molit")


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


def _parse_response(text: str, lawd_cd: str, deal_ymd: str) -> tuple[list[dict], int]:
    """
    정상 응답(XML)이면 (item 목록, 전체 건수 totalCount)를 반환하고,
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

    items = [
        {child.tag: (child.text or "").strip() for child in item_el}
        for item_el in root.findall("body/items/item")
    ]
    try:
        total_count = int(root.findtext("body/totalCount") or len(items))
    except ValueError:
        total_count = len(items)
    return items, total_count


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


MOLIT_PAGE_SIZE = 1000
MOLIT_MAX_PAGES = 20  # 무한루프 방지 안전장치 (한 지역·유형·월이 2만 건을 넘을 일은 없다)


def _fetch_one(lawd_cd: str, property_type: str, deal_ymd: str) -> list[dict]:
    """(법정동코드, 유형, 월) 1건을 조회해서 raw item 목록으로 돌려준다. 실패 시 MolitApiError.

    페이지네이션(2026-09-28): 예전엔 numOfRows=300으로 한 번만 호출해서, 거래가 300건을 넘는
    지역·유형·월은 뒤쪽이 통째로 잘렸다 (실측: 관악구 오피스텔 2026.8은 314건인데 300건만 수집,
    광진구·강동구 유형별 후보가 정확히 600건=300건x2개월로 잘린 흔적). 응답의 totalCount를 보고
    전부 가져올 때까지 pageNo를 올려가며 반복 호출한다."""
    url = MOLIT_BASE_URL + PROPERTY_TYPES[property_type]["endpoint"]
    collected: list[dict] = []

    for page_no in range(1, MOLIT_MAX_PAGES + 1):
        params = {
            "serviceKey": settings.data_go_kr_api_key,
            "LAWD_CD": lawd_cd,
            "DEAL_YMD": deal_ymd,
            "numOfRows": MOLIT_PAGE_SIZE,
            "pageNo": page_no,
        }
        try:
            res = requests.get(url, params=params, timeout=REQUEST_TIMEOUT_SEC)
            res.raise_for_status()
        except requests.RequestException as e:
            raise MolitApiError(
                f"국토교통부 API 호출 실패 ({property_type}, LAWD_CD={lawd_cd}, {deal_ymd}, page={page_no}): {e}"
            ) from e

        items, total_count = _parse_response(res.text, lawd_cd, deal_ymd)
        collected.extend(items)
        if not items or len(collected) >= total_count:
            break

    return collected


# ---------------------------------------------------------------------------------------------
# 매물 카드의 "실거래 참고" 실시간 조회 (GET /api/v1/listings/{listing_id}/reference) - 2026-09-30,
# 2026-10-01에 카드가 뜨자마자(펼치기 없이) 바로 불러오도록 바뀜. 매물마다 CSV에 미리 박아둔 고정값
# 대신, 그 매물의 법정동+지번으로 매번 실시간 조회한다(최근 2년, 매물당 최대 5건). 조회 단위
# (자치구+유형)로 6시간 캐시를 둬서, 같은 자치구 매물 카드가 연달아 떠도 매번 국토부 API를 새로
# 부르지 않는다. fetch_reference_transactions(건물 단위, 지번 일치)가 못 찾으면(단독·다가구처럼
# 애초에 지번이 없는 유형 포함) fetch_by_signature(더미 매물의 원본 거래 재발견) -> 그래도 안 되면
# fetch_neighborhood_transactions(동 단위)로 대신 보여준다 - listing_reference.py의 엔드포인트가
# 이 우선순위로 호출한다.
# ---------------------------------------------------------------------------------------------
_reference_cache: dict[tuple, tuple[float, list[dict]]] = {}
_reference_cache_lock = threading.Lock()


def _fetch_reference_records(lawd_cd: str, property_type: str, months: int) -> list[dict]:
    """(법정동코드, 매물유형)의 최근 N개월 실거래 raw item 목록 (캐시됨). fetch_reference_transactions/
    fetch_neighborhood_transactions이 공유해서 쓴다."""
    cache_key = (lawd_cd, property_type, months)
    with _reference_cache_lock:
        cached = _reference_cache.get(cache_key)
    if cached and time.time() - cached[0] < CACHE_TTL_SEC:
        return cached[1]

    def _fetch(deal_ymd: str) -> list[dict]:
        try:
            return _fetch_one(lawd_cd, property_type, deal_ymd)
        except MolitApiError as e:
            logger.warning(str(e))
            return None

    results = list(_HTTP_POOL.map(_fetch, _recent_deal_ymds(months)))
    had_error = any(r is None for r in results)
    records = [item for r in results if r is not None for item in r]
    if not had_error:
        with _reference_cache_lock:
            _reference_cache[cache_key] = (time.time(), records)
    return records


def _sort_recent(records: list[dict]) -> list[dict]:
    return sorted(records, key=lambda r: (r.get("dealYear", ""), r.get("dealMonth", ""), r.get("dealDay", "")), reverse=True)


REFERENCE_MONTHS_BACK = 24  # "실거래 참고"는 최근 2년치를 보여준다 (2026-10-01)
REFERENCE_LIMIT = 5  # 매물당 최대 5건만 보여준다


def fetch_reference_transactions(
    lawd_cd: str, property_type: str, dong: str, jibun: str, months: int = REFERENCE_MONTHS_BACK, limit: int = REFERENCE_LIMIT
) -> list[dict]:
    """(법정동코드, 매물유형)의 최근 N개월(기본 2년) 실거래 중 같은 법정동+지번(이 건물)인 것만 계약일
    내림차순 상위 limit건(기본 5건) 반환한다. 지번이 비어있으면(단독다가구 등 지번 자체가 없는 매물) 빈 목록."""
    if not jibun:
        return []
    records = _fetch_reference_records(lawd_cd, property_type, months)
    matched = [r for r in records if r.get("umdNm") == dong and (r.get("jibun") or "").strip() == jibun]
    return _sort_recent(matched)[:limit]


def fetch_neighborhood_transactions(
    lawd_cd: str, property_type: str, dong: str, months: int = REFERENCE_MONTHS_BACK, limit: int = REFERENCE_LIMIT
) -> list[dict]:
    """단독·다가구처럼 지번 자체가 없어 "이 건물"을 짚을 수 없는 매물유형을 위한 대안: 같은
    법정동(umdNm)의 최근 실거래를 동 단위 참고 시세로 돌려준다(건물을 특정하지 않음).
    국토교통부 단독·다가구 실거래 API 응답 자체에 지번 필드가 없어(2026-09-30 실측:
    buildYear/contractTerm/contractType/deal*/deposit/houseType/monthlyRent/pre*/sggCd/
    totalFloorAr/umdNm/useRRRight 뿐) 이 매물유형은 fetch_reference_transactions로 절대
    "이 건물"을 찾을 수 없다 - 행정안전부 도로명주소나 SGIS 같은 다른 공공 API를 더 엮어도
    원본 데이터에 없는 지번을 복원할 수는 없다."""
    records = _fetch_reference_records(lawd_cd, property_type, months)
    matched = [r for r in records if r.get("umdNm") == dong]
    return _sort_recent(matched)[:limit]


def _num(raw) -> int | None:
    v = _parse_amount(raw)
    return round(v) if v is not None else None


# fetch_by_signature 전용 캐시: (법정동코드, 유형, 계약월) -> raw item 목록. 카드를 펼칠 때만 조회하던
# 때와 달리(2026-10-01) 매물 카드가 뜨자마자 자동으로 불러오므로, 캐시가 없으면 스크롤 한 배치(10건)
# 마다 캐시 없는 국토부 API 호출이 그대로 나간다 - 같은 자치구·유형 단독다가구는 계약월이 겹치는
# 경우가 많아 이 캐시로 상당수 호출을 나눠 쓸 수 있다.
_signature_cache: dict[tuple, tuple[float, list[dict]]] = {}
_signature_cache_lock = threading.Lock()


def _fetch_one_cached(lawd_cd: str, property_type: str, deal_ymd: str) -> list[dict]:
    cache_key = (lawd_cd, property_type, deal_ymd)
    with _signature_cache_lock:
        cached = _signature_cache.get(cache_key)
    if cached and time.time() - cached[0] < CACHE_TTL_SEC:
        return cached[1]
    try:
        records = _fetch_one(lawd_cd, property_type, deal_ymd)
    except MolitApiError as e:
        logger.warning(str(e))
        return []
    with _signature_cache_lock:
        _signature_cache[cache_key] = (time.time(), records)
    return records


def fetch_by_signature(
    lawd_cd: str, property_type: str, dong: str, deal_ymd: str, deal_day: int, deposit: int, monthly_rent: int,
) -> list[dict]:
    """더미 매물(gen_dummyhouses.py)은 애초에 "이 매물은 이 국토부 실거래 건을 그대로 본떠 만든
    것"이라는 정답을 알고 있다 - CSV의 ref_계약일/ref_보증금/ref_월세(원본 그대로 저장, 매물 자체의
    보증금/월세는 표시용으로 ±8~12% 랜덤화된 것과 다름)가 그 정답이다. 단독·다가구는 지번이 없어
    fetch_reference_transactions로 "같은 건물"을 짚을 수 없지만, 그 계약이 있었던 딱 한 달만 받아
    같은 동+보증금+월세+계약일로 걸러내면 사실상 유일하게(광진구 254건 중 정확히 1건, 2026-09-30
    실측) 원본 거래 그 자체를 다시 찾아낼 수 있다 - "이 건물의 실거래"라고 불러도 되는 유일한 방법.
    신규 등록 매물(Part C)은 이런 원본 거래 자체가 없으므로 이 함수를 쓰지 않는다."""
    records = _fetch_one_cached(lawd_cd, property_type, deal_ymd)
    matched = [
        r for r in records
        if r.get("umdNm") == dong
        and _num(r.get("deposit")) == deposit
        and _num(r.get("monthlyRent")) == monthly_rent
        and str(r.get("dealDay", "")).lstrip("0") == str(deal_day)
    ]
    return matched

