"""
[담당: 송귀성] 행정안전부 도로명주소 안내시스템(business.juso.go.kr) 연동

국토교통부 실거래가 API(api_collector.py)는 아파트만 도로명(roadnm)을 자체적으로 주고,
오피스텔/연립다세대는 지번(jibun)만 준다 (단독다가구는 지번도 없어 동 단위가 한계).
이 모듈은 그 지번주소를 도로명주소로 변환하고, 건물명이 없는 매물의 공식 건물명도 보완한다
(이 둘은 모두 도로명주소 검색 API 응답 하나로 처리된다). 상세주소 API는 그와 별개로, 동(棟)이
여러 개인 복합건물에서 실거래 1건이 정확히 어느 동인지 모호한 문제를 다룬다 - 아래 2) 참고.

주의: data.go.kr의 "행정안전부_법정동코드" API와는 완전히 다른 서비스다. 법정동코드 API는
법정동코드<->지역명 코드표 조회용이고(app/data/lawd_codes.json에 고정값으로 이미 있음),
실제 지번->도로명 주소 변환/건물 상세정보는 이 business.juso.go.kr API가 담당한다
(키 발급도 별도 - 아래 두 API는 서로 다른 승인키를 쓴다).

1) 도로명주소 검색 API (JUSO_API_KEY, confmKey) - 지번/키워드로 도로명주소를 찾는다:
    GET https://business.juso.go.kr/addrlink/addrLinkApi.do
        ?confmKey=...&currentPage=1&countPerPage=1&keyword=서울특별시 강남구 자곡동 662&resultType=json
    응답 juso[0]에 도로명주소(roadAddr)뿐 아니라 admCd/rnMgtSn/udrtYn/buldMnnm/buldSlno도 같이
    온다 - 이 5개 값이 바로 아래 2) 상세주소 API의 입력값이라 검색 결과를 그대로 재사용한다.

2) 상세주소 API (JUSO_API_KEY_AV, 별도 confmKey) - 1)의 결과(admCd/rnMgtSn/udrtYn/buldMnnm/
   buldSlno)로 그 건물번지에 있는 동(棟) 목록을 조회한다 (2026-09-28 실측 응답: results.juso가
   [{dongNm: "A동", ...}, {dongNm: "B동", ...}, ...] 형태 - "대표 건물명" 필드가 아니라 "그 번지에
   동이 몇 개 있는지" 목록이다). 실거래 1건이 그중 정확히 어느 동인지는 MOLIT 데이터에 없어 알 수
   없으므로, 동이 정확히 1개뿐일 때(totalCount==1, 여러 동으로 나뉜 복합건물이 아님)만 그 동명을
   신뢰하고 쓴다 - 2개 이상이면 모호하니 그대로 버린다.
   건물명 자체(bdNm, 예: "강남 지웰홈스")는 이미 1) 검색 API 응답에 들어있어 이 API가 필요 없다.
    GET https://business.juso.go.kr/addrlink/addrDetailApi.do
        ?confmKey=...&admCd=...&rnMgtSn=...&udrtYn=0&buldMnnm=...&buldSlno=0&resultType=json

주소/건물명은 한 번 확정되면 바뀌지 않으므로(재개발 등 예외적인 경우 제외), TTL 없이 영구 캐시한다
(같은 건물이 여러 실거래 건에 반복 등장해서 캐시 적중률이 높다 - api_collector.py도 같은 이유로
6시간 TTL 캐시를 쓴다).
"""
import logging
import threading
import time

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

SEARCH_API_URL = "https://business.juso.go.kr/addrlink/addrLinkApi.do"
DETAIL_API_URL = "https://business.juso.go.kr/addrlink/addrDetailApi.do"
REQUEST_TIMEOUT_SEC = 3

# 서킷 브레이커: business.juso.go.kr이 대량 동시 요청에서 커넥션 타임아웃을 대거 내는 게 실측으로
# 확인됐다 (2026-09-28, 통근범위를 넓혀 대상 지역이 많아지자 juso.go.kr 요청 수백 건이 몰렸고
# 상당수가 5초 커넥션 타임아웃 -> 진단 1건에 156초가 걸림. 요청을 줄이는 것만으로는 부족하고,
# 원격 서비스가 이미 응답을 못 주는 상태면 빠르게 포기해야 전체 진단이 안 느려진다).
# 연속 실패가 임계치를 넘으면 일정 시간 동안 아예 호출을 시도하지 않고 즉시 실패 처리한다
# (냉각 시간이 지나면 다시 한 번 시도해서 서비스가 복구됐는지 확인한다 - 표준 서킷 브레이커 패턴).
_CIRCUIT_FAILURE_THRESHOLD = 5
_CIRCUIT_COOLDOWN_SEC = 30
_circuit_lock = threading.Lock()
_circuit_consecutive_failures = 0
_circuit_opened_at: float | None = None

_search_cache: dict[str, dict | None] = {}
_search_cache_lock = threading.Lock()
_detail_cache: dict[tuple, str | None] = {}
_detail_cache_lock = threading.Lock()


class JusoApiError(Exception):
    """도로명주소 API 호출/응답 실패 (호출부에서 잡아서 지번주소/기존 건물명으로 폴백시킨다)."""


class CircuitOpenError(JusoApiError):
    """juso.go.kr이 최근 연속으로 실패해서 냉각 시간 동안 호출 자체를 건너뛴다."""


def _circuit_is_open() -> bool:
    with _circuit_lock:
        if _circuit_opened_at is None:
            return False
        if time.time() - _circuit_opened_at >= _CIRCUIT_COOLDOWN_SEC:
            return False  # 냉각 시간 종료 - 이번 호출을 "시험 호출"로 통과시킨다
        return True


def _circuit_record_result(success: bool) -> None:
    global _circuit_consecutive_failures, _circuit_opened_at
    with _circuit_lock:
        if success:
            _circuit_consecutive_failures = 0
            _circuit_opened_at = None
            return
        _circuit_consecutive_failures += 1
        if _circuit_consecutive_failures >= _CIRCUIT_FAILURE_THRESHOLD:
            _circuit_opened_at = time.time()


def is_enabled() -> bool:
    """도로명주소 검색 API(1차 변환) 사용 가능 여부. 상세주소 API는 is_detail_enabled() 참고."""
    return bool(settings.juso_api_key)


def is_detail_enabled() -> bool:
    return bool(settings.juso_api_key_av)


def _get_json(url: str, params: dict, error_context: str) -> dict:
    if _circuit_is_open():
        raise CircuitOpenError(f"{error_context}: juso.go.kr 연속 실패로 {_CIRCUIT_COOLDOWN_SEC}초간 호출 건너뜀")

    try:
        res = requests.get(url, params=params, timeout=REQUEST_TIMEOUT_SEC)
        res.raise_for_status()
        body = res.json()
    except (requests.RequestException, ValueError) as e:
        _circuit_record_result(success=False)
        raise JusoApiError(f"{error_context} 호출 실패: {e}") from e

    _circuit_record_result(success=True)
    return body


def _check_common(body: dict, error_context: str) -> bool:
    """errorCode "0"이면 True. "4"(검색결과 없음)면 False(정상 흐름). 그 외는 예외."""
    common = body.get("results", {}).get("common", {})
    error_code = common.get("errorCode")
    if error_code == "0":
        return True
    if error_code == "4":
        return False
    raise JusoApiError(f"{error_context} 오류: {error_code} - {common.get('errorMessage')}")


def _search(keyword: str) -> dict | None:
    """도로명주소 검색 API 원본 결과(juso[0])를 캐싱해서 반환한다.
    roadAddr뿐 아니라 상세주소 API 입력값(admCd/rnMgtSn/udrtYn/buldMnnm/buldSlno)도 들어있다."""
    with _search_cache_lock:
        if keyword in _search_cache:
            return _search_cache[keyword]

    params = {
        "confmKey": settings.juso_api_key,
        "currentPage": 1,
        "countPerPage": 1,
        "keyword": keyword,
        "resultType": "json",
    }
    body = _get_json(SEARCH_API_URL, params, f"도로명주소 검색 API(keyword={keyword})")
    found = _check_common(body, f"도로명주소 검색 API(keyword={keyword})")
    juso = (body.get("results", {}).get("juso") or [None])[0] if found else None

    with _search_cache_lock:
        _search_cache[keyword] = juso
    return juso


def resolve_road_address(keyword: str) -> str | None:
    """지번주소(예: "서울특별시 강남구 자곡동 662")를 도로명주소로 변환한다.
    매칭 결과가 없으면 None (호출부는 지번주소를 그대로 표시하면 된다)."""
    juso = _search(keyword)
    return juso.get("roadAddr") if juso else None


def resolve_building_name(keyword: str) -> str | None:
    """건물명이 없는 매물(연립다세대 등)을 위해 공식 건물명(bdNm)을 보완한다.
    도로명주소 검색 API(1차, JUSO_API_KEY) 응답에 이미 들어있는 필드라 상세주소 API 호출 없이도
    된다 - 검색 자체가 실패/무매칭이면 None."""
    juso = _search(keyword)
    return (juso.get("bdNm") or None) if juso else None


def resolve_unambiguous_dong(keyword: str) -> str | None:
    """그 번지에 동(棟)이 정확히 1개뿐일 때만 동명을 상세주소 API로 확인해서 반환한다 (2개 이상이면
    실거래 1건이 그중 어느 동인지 MOLIT 데이터로는 알 수 없어 모호하므로 None - 모듈 docstring 참고).
    JUSO_API_KEY_AV 미설정이거나 검색 자체가 안 되면 None."""
    if not is_detail_enabled():
        return None

    juso = _search(keyword)
    if not juso:
        return None

    detail_key = (juso.get("admCd"), juso.get("rnMgtSn"), juso.get("udrtYn"), juso.get("buldMnnm"), juso.get("buldSlno"))
    with _detail_cache_lock:
        if detail_key in _detail_cache:
            return _detail_cache[detail_key]

    params = {
        "confmKey": settings.juso_api_key_av,
        "currentPage": 1,
        "countPerPage": 10,  # 동 개수가 1개인지 판별해야 하므로 목록 전체가 필요
        "admCd": juso.get("admCd"),
        "rnMgtSn": juso.get("rnMgtSn"),
        "udrtYn": juso.get("udrtYn"),
        "buldMnnm": juso.get("buldMnnm"),
        "buldSlno": juso.get("buldSlno"),
        "resultType": "json",
    }
    body = _get_json(DETAIL_API_URL, params, f"상세주소 API(keyword={keyword})")
    found = _check_common(body, f"상세주소 API(keyword={keyword})")
    dong_list = (body.get("results", {}).get("juso") or []) if found else []
    dong_name = dong_list[0].get("dongNm") if len(dong_list) == 1 else None

    with _detail_cache_lock:
        _detail_cache[detail_key] = dong_name
    return dong_name
