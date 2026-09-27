"""
[담당: 송귀성] 카카오 로컬 API - 주소 -> 좌표 변환 (지도 핀을 실제 건물 위치에 정확히 찍기 위함)

api_collector.py/juso_api.py가 만든 도로명/지번주소 문자열을 실제 위경도로 바꿔서, map-util.js가
"지역당 마커 1개"(구청 근사 좌표) 대신 매물별 정확한 위치에 핀을 찍을 수 있게 한다.

    GET https://dapi.kakao.com/v2/local/search/address.json?query={주소}
        Header: Authorization: KakaoAK {REST API 키}

kakao_mobility.py/kakao_routing.py와 같은 REST API 키(KAKAO_REST_APP_KEY)를 쓴다 - 카카오
디벨로퍼스 앱에서 "카카오맵" API 사용 설정을 이미 켰다면(대중교통/도보 조회 때문에) 추가 설정 없이
바로 된다 (로컬 API는 기본 활성화 항목).

juso_api.py처럼 서킷 브레이커를 둔다 - 2026-09-28에 juso.go.kr이 대량 동시 요청에서 타임아웃을
대거 내서 진단 1건이 156초까지 걸린 적이 있어(그때 얻은 교훈), 카카오 로컬 API도 만약을 대비해
같은 안전장치를 넣는다. 주소는 한 번 확정되면 좌표가 바뀌지 않으므로 TTL 없이 영구 캐시한다.
"""
import logging
import threading
import time

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

GEOCODE_API_URL = "https://dapi.kakao.com/v2/local/search/address.json"
REQUEST_TIMEOUT_SEC = 3

_CIRCUIT_FAILURE_THRESHOLD = 5
_CIRCUIT_COOLDOWN_SEC = 30
_circuit_lock = threading.Lock()
_circuit_consecutive_failures = 0
_circuit_opened_at: float | None = None

_cache: dict[str, tuple[float, float] | None] = {}
_cache_lock = threading.Lock()


class KakaoGeocodeError(Exception):
    """카카오 로컬 API 호출/응답 실패 (호출부에서 잡아서 좌표 없이 폴백시킨다)."""


class CircuitOpenError(KakaoGeocodeError):
    """카카오 로컬 API가 최근 연속으로 실패해서 냉각 시간 동안 호출 자체를 건너뛴다."""


def is_enabled() -> bool:
    return bool(settings.kakao_rest_app_key)


def _circuit_is_open() -> bool:
    with _circuit_lock:
        if _circuit_opened_at is None:
            return False
        return time.time() - _circuit_opened_at < _CIRCUIT_COOLDOWN_SEC


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


def geocode_address(address: str) -> tuple[float, float] | None:
    """주소 문자열 -> (위도, 경도). 매칭 결과가 없으면 None (호출부는 좌표 없이 폴백하면 된다)."""
    with _cache_lock:
        if address in _cache:
            return _cache[address]

    if _circuit_is_open():
        raise CircuitOpenError(f"카카오 로컬 API 연속 실패로 {_CIRCUIT_COOLDOWN_SEC}초간 호출 건너뜀")

    headers = {"Authorization": f"KakaoAK {settings.kakao_rest_app_key}"}
    try:
        res = requests.get(
            GEOCODE_API_URL, headers=headers, params={"query": address}, timeout=REQUEST_TIMEOUT_SEC
        )
        res.raise_for_status()
        body = res.json()
    except (requests.RequestException, ValueError) as e:
        _circuit_record_result(success=False)
        raise KakaoGeocodeError(f"카카오 로컬 API 호출 실패(query={address}): {e}") from e

    _circuit_record_result(success=True)
    documents = body.get("documents") or []
    coord = (float(documents[0]["y"]), float(documents[0]["x"])) if documents else None

    with _cache_lock:
        _cache[address] = coord
    return coord
