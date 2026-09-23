"""
[담당: 송귀성] 카카오맵 REST API - 대중교통/도보 경로 조회 (통근시간 실제 계산)

카카오모빌리티 길찾기 API(kakao_mobility.py)는 자동차 경로만 제공하지만, 카카오맵 REST API는
대중교통/도보 경로 조회를 별도로 제공한다. "주요 통근 수단"(transport_type)이 PUBLIC/WALK일 때
이 모듈로 실제 소요시간을 구하고, CAR는 kakao_mobility.py를 쓴다 (calculator.py의
_commute_minutes가 분기).

    GET https://dapi.kakao.com/v2/routing/publictraffic?start_x=..&start_y=..&end_x=..&end_y=..
    GET https://dapi.kakao.com/v2/routing/walk?start_x=..&start_y=..&end_x=..&end_y=..
        Header: Authorization: KakaoAK {REST API 키}

두 API 모두 카카오모빌리티와 같은 REST API 키(KAKAO_REST_APP_KEY)를 쓰지만, 카카오 디벨로퍼스
앱에서 "카카오맵" API를 별도로 [사용 설정] ON 해야 동작한다 (2026-09-23 실제 호출로 확인 완료).
대중교통은 여러 경로 후보(routes[])를 주는데, 그중 가장 빠른(totalTime 최소) 경로를 쓴다.
도보는 목적지가 너무 멀면(status: TOO_FAR_AWAY 등) 결과가 없는데, 이 경우도 다른 실패와
동일하게 예외를 던져 calculator.py가 직선거리 추정으로 폴백하게 한다.

서비스키 발급: https://developers.kakao.com -> 내 애플리케이션 -> [카카오맵] > [사용 설정] ON ->
              앱 키의 "REST API 키"를 customhouse-ai/.env의 KAKAO_REST_APP_KEY 에 넣는다.
"""
import logging
import threading
import time

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

PUBLIC_TRANSIT_URL = "https://dapi.kakao.com/v2/routing/publictraffic"
WALK_URL = "https://dapi.kakao.com/v2/routing/walk"
REQUEST_TIMEOUT_SEC = 5

# 캐시: (수단, 반올림한 출발/도착 좌표) -> (저장 시각, 소요시간(분)). kakao_mobility.py와 같은 이유로
# 하루 TTL을 둔다.
CACHE_TTL_SEC = 24 * 60 * 60

_cache: dict[tuple, tuple[float, int]] = {}
_cache_lock = threading.Lock()


class KakaoRoutingError(Exception):
    """카카오맵 경로 조회 API 호출/응답 실패 (호출부에서 잡아 직선거리 추정으로 폴백한다)."""


def is_enabled() -> bool:
    """KAKAO_REST_APP_KEY가 설정되어 있어야 실 API를 시도한다. 없으면 즉시 폴백."""
    return bool(settings.kakao_rest_app_key)


def _round_coord(v: float) -> float:
    return round(v, 4)  # 약 11m 오차. 통근시간 추정 목적으로는 무시할 수준.


def _get_cached(cache_key: tuple) -> int | None:
    with _cache_lock:
        cached = _cache.get(cache_key)
    if cached and time.time() - cached[0] < CACHE_TTL_SEC:
        return cached[1]
    return None


def _set_cached(cache_key: tuple, minutes: int) -> None:
    with _cache_lock:
        _cache[cache_key] = (time.time(), minutes)


def _request(url: str, origin_lat: float, origin_lon: float, dest_lat: float, dest_lon: float) -> dict:
    headers = {"Authorization": f"KakaoAK {settings.kakao_rest_app_key}"}
    params = {
        "start_x": origin_lon,
        "start_y": origin_lat,
        "end_x": dest_lon,
        "end_y": dest_lat,
    }
    try:
        res = requests.get(url, headers=headers, params=params, timeout=REQUEST_TIMEOUT_SEC)
        res.raise_for_status()
        return res.json()
    except requests.RequestException as e:
        raise KakaoRoutingError(f"카카오맵 경로 조회 API 호출 실패({url}): {e}") from e
    except ValueError as e:
        raise KakaoRoutingError(f"카카오맵 경로 조회 응답 파싱 실패({url}): {e}") from e


def fetch_public_transit_minutes(origin_lat: float, origin_lon: float, dest_lat: float, dest_lon: float) -> int:
    """대중교통 경로 중 가장 빠른(totalTime 최소) 소요시간(분). 실패 시 KakaoRoutingError."""
    cache_key = ("public", _round_coord(origin_lat), _round_coord(origin_lon),
                 _round_coord(dest_lat), _round_coord(dest_lon))
    cached = _get_cached(cache_key)
    if cached is not None:
        return cached

    body = _request(PUBLIC_TRANSIT_URL, origin_lat, origin_lon, dest_lat, dest_lon)
    if body.get("status") != "OK":
        raise KakaoRoutingError(f"카카오맵 대중교통 경로 조회 실패: {body.get('status')}")

    routes = body.get("routes") or []
    if not routes:
        raise KakaoRoutingError("카카오맵 대중교통 경로 결과 없음")

    fastest_sec = min(r["properties"]["totalTime"] for r in routes)
    minutes = max(1, round(fastest_sec / 60))
    _set_cached(cache_key, minutes)
    return minutes


def fetch_walk_minutes(origin_lat: float, origin_lon: float, dest_lat: float, dest_lon: float) -> int:
    """도보 경로 소요시간(분). 실패 시(너무 먼 거리 포함) KakaoRoutingError."""
    cache_key = ("walk", _round_coord(origin_lat), _round_coord(origin_lon),
                 _round_coord(dest_lat), _round_coord(dest_lon))
    cached = _get_cached(cache_key)
    if cached is not None:
        return cached

    body = _request(WALK_URL, origin_lat, origin_lon, dest_lat, dest_lon)
    if body.get("status") != "OK":
        raise KakaoRoutingError(f"카카오맵 도보 경로 조회 실패: {body.get('status')}")

    minutes = max(1, round(body["route"]["properties"]["totalTime"] / 60))
    _set_cached(cache_key, minutes)
    return minutes
