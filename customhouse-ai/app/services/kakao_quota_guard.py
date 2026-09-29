"""
[담당: 송귀성] 카카오 REST API 공용 - 일일 호출 한도(quota) 초과 감지 + 짧은 회로차단(circuit breaker)

카카오맵 경로 조회(kakao_routing.py)와 카카오모빌리티 길찾기(kakao_mobility.py)는 같은 REST API 키
(KAKAO_REST_APP_KEY)를 쓰고 같은 일일 호출 한도를 공유한다. 한도를 넘으면 카카오는 모든 요청에
"API limit has been exceeded"(HTTP 400, code -10)를 돌려주는데, 기존 코드는 이걸 "가끔 나는 일시적
오류"와 구분하지 못해 최종 추천 매물마다(최대 2000건) 최대 2회씩 재시도하며 계속 API를 호출했다.
어차피 전부 실패해 직선거리 추정으로 폴백하는데 그 실패를 확인하는 데만 진단이 수십 초씩 걸렸다
(2026-09-29: 관리자 대출 조건 기능을 테스트하며 하루 호출 한도를 다 써서 실제로 겪은 문제).

한도 초과가 한 번 감지되면 BACKOFF_SEC 동안은 실제 HTTP 호출을 하지 않고 바로 실패 처리해서, 그 사이의
모든 통근시간 계산이 곧장 직선거리 추정으로 넘어가게 한다 (카카오 무료 한도는 보통 자정에 초기화되지만
짧은 버스트 제한일 수도 있어, 하루 종일 막아두지 않고 몇 분마다 한 번씩만 다시 확인한다).
"""
import threading
import time

BACKOFF_SEC = 5 * 60  # 한도 초과가 감지되면 이 시간 동안은 재시도하지 않는다

_lock = threading.Lock()
_blocked_until = 0.0


def is_blocked() -> bool:
    """지금 호출을 건너뛰어야 하면 True (직전에 한도 초과를 감지해 대기 중)."""
    with _lock:
        return time.time() < _blocked_until


def is_quota_exceeded_response(status_code: int, body_text: str) -> bool:
    """카카오의 "호출 한도 초과" 응답인지 판별한다: HTTP 429, 또는 400 + 본문에 한도 초과 문구/코드.
    (실측: {"errorType":"BadRequest","message":"API limit has been exceeded.","code":-10, ...})"""
    if status_code == 429:
        return True
    text = (body_text or "").lower()
    return "limit has been exceeded" in text or '"code":-10' in text


def report_quota_exceeded() -> bool:
    """한도 초과를 기록해 앞으로 BACKOFF_SEC 동안 호출을 건너뛰게 한다.
    @return 이 호출로 새로 차단 상태가 됐으면 True (호출부가 경고 로그를 한 번만 남기도록)."""
    global _blocked_until
    with _lock:
        was_already_blocked = time.time() < _blocked_until
        _blocked_until = time.time() + BACKOFF_SEC
        return not was_already_blocked
