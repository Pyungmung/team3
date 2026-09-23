"""
[담당: 송귀성] 카카오모빌리티 길찾기(자동차) REST API - 자차 통근 실제 소요시간

    GET https://apis-navi.kakaomobility.com/v1/directions
        ?origin={경도},{위도}&destination={경도},{위도}
        Header: Authorization: KakaoAK {REST API 키}

카카오 길찾기 API 5종(자동차/다중경유지/다중출발지/다중목적지/미래운행정보) 전부 "자동차" 경로만
제공하고 대중교통·도보 경로는 지원하지 않는다. 그래서 "주요 통근 수단"(transport_type)이 CAR일 때만
이 API로 실제 소요시간을 구하고, PUBLIC/WALK는 기존처럼 calculator.py의 직선거리 기반 추정치를 쓴다.

서비스키 발급: https://developers.kakao.com -> 애플리케이션 추가 -> 앱 키의 "REST API 키"를
              customhouse-ai/.env의 KAKAO_REST_APP_KEY 에 넣는다.
              (지도 표시용 KAKAO_MAP_APP_KEY의 JavaScript 키와는 다른 키다.)
"""
import logging
import threading
import time

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

KAKAO_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions"
REQUEST_TIMEOUT_SEC = 5

# 캐시: (반올림한 출발/도착 좌표) -> (저장 시각, 소요시간(분)). 동일 직장 위치 x 후보 지역 조합이
# 요청마다 반복되므로 캐시로 API 호출량을 크게 줄인다. 도로 상황은 시시각각 바뀌지만 통근시간
# "추정" 용도로는 하루 캐시로 충분하다.
CACHE_TTL_SEC = 24 * 60 * 60

_cache: dict[tuple, tuple[float, int]] = {}
_cache_lock = threading.Lock()


class KakaoMobilityError(Exception):
    """카카오모빌리티 길찾기 API 호출/응답 실패 (호출부에서 잡아 직선거리 추정으로 폴백한다)."""


def is_enabled() -> bool:
    """KAKAO_REST_APP_KEY가 설정되어 있어야 실 API를 시도한다. 없으면 즉시 폴백."""
    return bool(settings.kakao_rest_app_key)


def _round_coord(v: float) -> float:
    # 소수점 4자리 ≈ 약 11m 오차. 통근시간 추정 목적으로는 무시할 수준이고, 캐시 히트율을 높인다.
    return round(v, 4)


def fetch_car_commute_minutes(origin_lat: float, origin_lon: float, dest_lat: float, dest_lon: float) -> int:
    """자동차 길찾기 API로 실제 예상 소요시간(분)을 구한다. 실패 시 KakaoMobilityError를 던진다."""
    cache_key = (
        _round_coord(origin_lat), _round_coord(origin_lon),
        _round_coord(dest_lat), _round_coord(dest_lon),
    )
    with _cache_lock:
        cached = _cache.get(cache_key)
    if cached and time.time() - cached[0] < CACHE_TTL_SEC:
        return cached[1]

    headers = {"Authorization": f"KakaoAK {settings.kakao_rest_app_key}"}
    params = {
        "origin": f"{origin_lon},{origin_lat}",
        "destination": f"{dest_lon},{dest_lat}",
    }
    try:
        res = requests.get(KAKAO_DIRECTIONS_URL, headers=headers, params=params, timeout=REQUEST_TIMEOUT_SEC)
        res.raise_for_status()
        body = res.json()
    except requests.RequestException as e:
        raise KakaoMobilityError(f"카카오모빌리티 길찾기 API 호출 실패: {e}") from e
    except ValueError as e:
        raise KakaoMobilityError(f"카카오모빌리티 응답 파싱 실패: {e}") from e

    routes = body.get("routes") or []
    if not routes or routes[0].get("result_code") != 0:
        detail = routes[0].get("result_msg") if routes else body
        raise KakaoMobilityError(f"카카오모빌리티 길찾기 실패: {detail}")

    duration_sec = routes[0]["summary"]["duration"]
    minutes = max(1, round(duration_sec / 60))

    with _cache_lock:
        _cache[cache_key] = (time.time(), minutes)

    return minutes
