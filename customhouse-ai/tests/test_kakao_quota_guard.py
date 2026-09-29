"""
[담당: 송귀성] 카카오 API 호출 한도 초과 감지/회로차단(kakao_quota_guard) 테스트.
실행 (customhouse-ai 폴더에서):  python tests/test_kakao_quota_guard.py
"""
import sys
import time
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import kakao_mobility, kakao_quota_guard, kakao_routing  # noqa: E402


def _reset():
    with kakao_quota_guard._lock:
        kakao_quota_guard._blocked_until = 0.0


def test_한도_초과_응답을_인식한다():
    _reset()
    assert kakao_quota_guard.is_quota_exceeded_response(429, "") is True
    assert kakao_quota_guard.is_quota_exceeded_response(
        400, '{"errorType":"BadRequest","message":"API limit has been exceeded.","code":-10}'
    ) is True
    assert kakao_quota_guard.is_quota_exceeded_response(400, '{"status":"TOO_FAR_AWAY"}') is False
    assert kakao_quota_guard.is_quota_exceeded_response(500, "internal error") is False


def test_한도_초과를_기록하면_그동안_차단된다():
    _reset()
    assert kakao_quota_guard.is_blocked() is False
    assert kakao_quota_guard.report_quota_exceeded() is True   # 처음 차단되는 순간에만 True
    assert kakao_quota_guard.is_blocked() is True
    assert kakao_quota_guard.report_quota_exceeded() is False  # 이미 차단 중이면 다시 알리지 않는다


def test_차단_시간이_지나면_다시_호출을_시도한다():
    _reset()
    orig = kakao_quota_guard.BACKOFF_SEC
    kakao_quota_guard.BACKOFF_SEC = 0.05
    try:
        kakao_quota_guard.report_quota_exceeded()
        assert kakao_quota_guard.is_blocked() is True
        time.sleep(0.08)
        assert kakao_quota_guard.is_blocked() is False
    finally:
        kakao_quota_guard.BACKOFF_SEC = orig


def _fake_response(status_code, text):
    r = mock.Mock()
    r.status_code = status_code
    r.text = text

    def raise_for_status():
        if status_code >= 400:
            import requests
            raise requests.HTTPError(f"{status_code} error")

    r.raise_for_status = raise_for_status
    r.json = lambda: {"status": "OK", "routes": [{"properties": {"totalTime": 600}}]}
    return r


def test_대중교통_경로_조회가_한도초과를_감지하면_한_번만_호출하고_차단한다():
    _reset()
    with mock.patch("app.services.kakao_routing.settings.kakao_rest_app_key", "test-key"), \
         mock.patch("app.services.kakao_routing.requests.get") as get:
        get.return_value = _fake_response(400, '{"message":"API limit has been exceeded.","code":-10}')
        try:
            kakao_routing.fetch_public_transit_minutes(37.5, 127.0, 37.6, 127.1)
            assert False, "예외가 나야 한다"
        except kakao_routing.KakaoRoutingError:
            pass
        assert get.call_count == 1  # 재시도(2회)하지 않고 한 번만 호출하고 바로 포기한다
        assert kakao_quota_guard.is_blocked() is True


def test_차단_중에는_카카오맵_API를_아예_호출하지_않는다():
    _reset()
    kakao_quota_guard.report_quota_exceeded()
    with mock.patch("app.services.kakao_routing.requests.get") as get:
        try:
            kakao_routing.fetch_walk_minutes(37.5, 127.0, 37.6, 127.1)
            assert False, "예외가 나야 한다"
        except kakao_routing.KakaoRoutingError:
            pass
        get.assert_not_called()


def test_카카오모빌리티도_한도초과를_감지하면_한_번만_호출한다():
    _reset()
    with mock.patch("app.services.kakao_mobility.settings.kakao_rest_app_key", "test-key"), \
         mock.patch("app.services.kakao_mobility.requests.get") as get:
        get.return_value = _fake_response(429, "")
        try:
            kakao_mobility.fetch_car_commute_minutes(37.5, 127.0, 37.6, 127.1)
            assert False, "예외가 나야 한다"
        except kakao_mobility.KakaoMobilityError:
            pass
        assert get.call_count == 1
        assert kakao_quota_guard.is_blocked() is True


def test_일반_실패는_기존처럼_두_번_재시도한다():
    _reset()
    with mock.patch("app.services.kakao_routing.settings.kakao_rest_app_key", "test-key"), \
         mock.patch("app.services.kakao_routing.requests.get") as get:
        get.return_value = _fake_response(500, "internal error")
        try:
            kakao_routing.fetch_public_transit_minutes(37.5, 127.0, 37.6, 127.1)
            assert False, "예외가 나야 한다"
        except kakao_routing.KakaoRoutingError:
            pass
        assert get.call_count == 2  # 한도 초과가 아니면 기존처럼 1회 재시도(총 2회)한다
        assert kakao_quota_guard.is_blocked() is False


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("PASS", name)
            except AssertionError:
                failed += 1
                print("FAIL", name)
                raise
    print("failures:", failed)
    sys.exit(1 if failed else 0)
