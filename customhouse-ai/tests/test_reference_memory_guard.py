# [담당: 송귀성] 실거래 참고 조회의 메모리 보호 - 동시 조회 제한 + 캐시 크기 제한 (2026-10-12)
# 배경: 무료 서버(512MB)에서 서로 다른 자치구의 실거래 조회가 동시에 몰리면 24개월치 raw 자료가 한꺼번에 메모리에 올라가 서버가 죽었다.
import sys
import threading
import time
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import api_collector  # noqa: E402


@pytest.fixture(autouse=True)
def _깨끗한_캐시와_문지기(monkeypatch):
    """테스트끼리 영향 주지 않게 캐시를 비우고, 문지기(세마포어)를 새로 만든다."""
    api_collector._reference_cache.clear()
    api_collector._signature_cache.clear()
    monkeypatch.setattr(api_collector, "_reference_fetch_gate", threading.BoundedSemaphore(api_collector.REFERENCE_FETCH_CONCURRENCY))
    yield
    api_collector._reference_cache.clear()
    api_collector._signature_cache.clear()


def test_캐시는_최대_개수를_넘으면_가장_오래_안_쓴_것부터_지운다(monkeypatch):
    monkeypatch.setattr(api_collector, "REFERENCE_CACHE_MAX_ENTRIES", 3)
    monkeypatch.setattr(api_collector, "_fetch_one", lambda lawd_cd, ptype, ymd: [{"umdNm": lawd_cd}])

    for code in ("A", "B", "C"):
        api_collector._fetch_reference_records(code, "오피스텔", 2)
    assert len(api_collector._reference_cache) == 3

    api_collector._fetch_reference_records("A", "오피스텔", 2)  # A를 다시 써서 '최근 사용'으로 올린다
    api_collector._fetch_reference_records("D", "오피스텔", 2)  # D가 들어오면 가장 오래 안 쓴 B가 밀려난다
    keys = [k[0] for k in api_collector._reference_cache]
    assert len(keys) == 3
    assert "B" not in keys and set(keys) == {"C", "A", "D"}


def test_한_달치_캐시도_최대_개수를_넘지_않는다(monkeypatch):
    monkeypatch.setattr(api_collector, "SIGNATURE_CACHE_MAX_ENTRIES", 4)
    monkeypatch.setattr(api_collector, "_fetch_one", lambda lawd_cd, ptype, ymd: [{"ymd": ymd}])
    for m in range(1, 10):
        api_collector._fetch_one_cached("11680", "오피스텔", f"2026{m:02d}")
    assert len(api_collector._signature_cache) == 4
    assert [k[2] for k in api_collector._signature_cache] == ["202606", "202607", "202608", "202609"], "가장 최근에 쓴 4개만 남는다"


def test_캐시가_있으면_국토부를_다시_부르지_않는다(monkeypatch):
    calls = []
    monkeypatch.setattr(api_collector, "_fetch_one", lambda lawd_cd, ptype, ymd: calls.append(ymd) or [{"x": 1}])
    api_collector._fetch_reference_records("11680", "오피스텔", 3)
    n = len(calls)
    assert n == 3
    api_collector._fetch_reference_records("11680", "오피스텔", 3)
    assert len(calls) == n, "두 번째는 캐시에서 바로 답한다"


def test_만료된_캐시는_다시_조회한다(monkeypatch):
    calls = []
    monkeypatch.setattr(api_collector, "_fetch_one", lambda lawd_cd, ptype, ymd: calls.append(ymd) or [])
    api_collector._fetch_reference_records("11680", "오피스텔", 2)
    key = ("11680", "오피스텔", 2)
    saved_at, records = api_collector._reference_cache[key]
    api_collector._reference_cache[key] = (saved_at - api_collector.CACHE_TTL_SEC - 1, records)
    api_collector._fetch_reference_records("11680", "오피스텔", 2)
    assert len(calls) == 4, "만료돼서 2개월치를 다시 받는다"


def test_서로_다른_자치구_조회가_동시에_몰려도_대량_조회는_동시에_2건까지만_한다(monkeypatch):
    in_flight = set()
    peak = 0
    guard = threading.Lock()

    def slow_fetch(lawd_cd, ptype, ymd):
        nonlocal peak
        with guard:
            in_flight.add(lawd_cd)
            peak = max(peak, len(in_flight))
        time.sleep(0.05)
        return [{"umdNm": lawd_cd}]

    monkeypatch.setattr(api_collector, "_fetch_one", slow_fetch)

    # 한 조회가 끝나는 시점(마지막 개월 조회가 끝날 때)에 in_flight 에서 빼기 위해, 조회 함수를 감싼다
    real = api_collector._fetch_reference_records

    def tracked(lawd_cd, ptype, months):
        try:
            return real(lawd_cd, ptype, months)
        finally:
            with guard:
                in_flight.discard(lawd_cd)

    results = {}

    def worker(code):
        results[code] = tracked(code, "오피스텔", 3)

    threads = [threading.Thread(target=worker, args=(f"S{i:02d}",)) for i in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=30)

    assert len(results) == 8, "8건 모두 끝까지 처리된다 (거절되지 않고 차례를 기다림)"
    assert peak <= api_collector.REFERENCE_FETCH_CONCURRENCY, f"동시에 올라간 자치구 묶음 수 {peak} > {api_collector.REFERENCE_FETCH_CONCURRENCY}"


def test_같은_자치구_조회가_동시에_와도_국토부는_한_번만_부른다(monkeypatch):
    calls = []
    lock = threading.Lock()

    def slow_fetch(lawd_cd, ptype, ymd):
        with lock:
            calls.append(ymd)
        time.sleep(0.05)
        return [{"umdNm": "x"}]

    monkeypatch.setattr(api_collector, "_fetch_one", slow_fetch)
    threads = [threading.Thread(target=api_collector._fetch_reference_records, args=("11680", "오피스텔", 2)) for _ in range(6)]
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=30)
    assert len(calls) == 2, f"2개월치를 한 번만 받아야 한다 (실제 {len(calls)}번) - 차례가 오면 캐시를 다시 확인한다"


def test_차례를_너무_오래_기다리면_오류로_돌려준다(monkeypatch):
    monkeypatch.setattr(api_collector, "REFERENCE_FETCH_WAIT_SEC", 0.05)
    gate = threading.BoundedSemaphore(1)
    gate.acquire()  # 문지기를 다른 조회가 계속 잡고 있는 상황
    monkeypatch.setattr(api_collector, "_reference_fetch_gate", gate)
    monkeypatch.setattr(api_collector, "_fetch_one", lambda *a: [])
    with pytest.raises(api_collector.MolitApiError):
        api_collector._fetch_reference_records("11680", "오피스텔", 2)


def test_조회가_실패해도_문지기를_돌려놓는다(monkeypatch):
    def boom(*a):
        raise RuntimeError("예상 밖 오류")

    monkeypatch.setattr(api_collector, "_fetch_one", boom)
    for _ in range(api_collector.REFERENCE_FETCH_CONCURRENCY + 2):
        with pytest.raises(RuntimeError):
            api_collector._fetch_reference_records("11680", "오피스텔", 2)
    # 문지기가 새지 않았다면 정상 조회가 막히지 않는다
    monkeypatch.setattr(api_collector, "_fetch_one", lambda *a: [{"ok": 1}])
    assert api_collector._fetch_reference_records("99999", "오피스텔", 2)
