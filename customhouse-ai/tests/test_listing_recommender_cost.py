"""
[담당: 송귀성] 더미 매물 추천의 "실질 주거비" 계산(listing_recommender._to_result) 테스트.
전세는 월세가 없어서, 매물 보증금 중 지금 가진 보증금(현재 보증금)으로 못 채우는 부족분(대출금액)에
"보증금액 전환 이자기회비용"과 같은 금리(deposit_rate_percent, 한국부동산원 API)를 적용한 이자를 관리비에 더한다.
(2026-09-29: 처음엔 임시 3% 고정값을 썼다가, 같은 전환율 API 값으로 통일했다)
실행 (customhouse-ai 폴더에서):  python tests/test_listing_recommender_cost.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import listing_recommender  # noqa: E402

RATE = 6.35  # 테스트에서 쓰는 전환율(예시값) - 실제 값은 reb_conversion_rate.get_metro_conversion_rate()가 정한다


def listing(lease_type="전세", deposit=20000, monthly_rent=0, maintenance_fee=5):
    return {
        "region": "강남구", "property_type": "아파트", "building_name": "테스트", "dong": None,
        "road_address": "서울시 강남구", "jibun_address": "", "lat": 37.5, "lon": 127.0,
        "exclusive_area": 20.0, "floor": "1", "lease_type": lease_type, "is_semi_jeonse": False,
        "monthly_rent": monthly_rent, "deposit": deposit, "maintenance_fee": maintenance_fee,
        "listing_id": "TEST-1", "listing_status": "계약가능", "registered_date": "2026-09-01",
        "photo": "", "unit_label": "", "maintenance_fee_items": "", "parking": "", "elevator": None,
        "rooms": None, "bathrooms": None, "built_year": None, "move_in_date": "", "description": "",
        "broker": {}, "postal_code": "", "address_source": "",
    }


def result(current_deposit, rate=RATE, **listing_kw):
    return listing_recommender._to_result(listing(**listing_kw), 30, "카카오 API", rate, current_deposit)


def test_전세는_부족분에_전환율_API_금리를_적용한_이자가_관리비에_더해진다():
    r = result(current_deposit=10000, deposit=20000, maintenance_fee=5)  # 부족분 10000
    expected_interest = round(10000 * RATE / 100 / 12)
    assert r["deposit_shortfall"] == 10000
    assert r["loan_interest"] == expected_interest
    assert r["real_housing_cost"] == 5 + expected_interest
    assert r["baseline_cost"] == r["real_housing_cost"]


def test_금리가_바뀌면_이자도_같이_바뀐다():
    # deposit_opportunity_cost(보증금액 전환 이자기회비용)와 같은 금리를 그대로 쓰는지 확인
    low = result(current_deposit=10000, deposit=20000, rate=3.0)
    high = result(current_deposit=10000, deposit=20000, rate=6.35)
    assert low["loan_interest"] < high["loan_interest"]
    assert high["loan_interest"] == round(10000 * 6.35 / 100 / 12)


def test_보유_보증금이_매물_보증금보다_많거나_같으면_대출이자와_부족분이_없다():
    same = result(current_deposit=20000, deposit=20000, maintenance_fee=5)
    more = result(current_deposit=25000, deposit=20000, maintenance_fee=5)
    for r in (same, more):
        assert r["deposit_shortfall"] == 0
        assert r["loan_interest"] == 0
        assert r["real_housing_cost"] == 5  # 관리비만


def test_월세는_대출이자와_부족분을_계산하지_않는다_기존과_동일():
    r = result(current_deposit=0, lease_type="월세", deposit=20000, monthly_rent=60, maintenance_fee=5)
    assert r["deposit_shortfall"] == 0
    assert r["loan_interest"] == 0
    assert r["real_housing_cost"] == 65  # 월세 + 관리비 (기존과 동일)


def test_대출이자와_보증금_기회비용은_금리는_같지만_원금이_달라_값이_다르다():
    # 대출이자(부족분 기준)와 보증금 기회비용(보증금 전체 기준)은 같은 금리, 다른 원금이라 값이 다르다
    r = result(current_deposit=10000, deposit=20000, maintenance_fee=0)
    assert r["loan_interest"] != r["deposit_opportunity_cost"]
    assert r["deposit_converted_cost"] == round(0 + 0 + r["deposit_opportunity_cost"], 1)


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


# 2026-10-03: 월세 매물의 예금 전환 이자기회비용은 전환율이 아니라 예금은행 정기예금(1년) 금리를 쓴다
DEPOSIT_RATE = 3.39


def test_월세_매물은_예금_전환_이자기회비용에_정기예금_금리를_쓴다():
    r = listing_recommender._to_result(
        listing(lease_type="월세", deposit=1000, monthly_rent=50, maintenance_fee=5), 30, "카카오 API", RATE, 0, DEPOSIT_RATE)
    assert r["deposit_opportunity_rate_percent"] == DEPOSIT_RATE
    assert r["deposit_opportunity_cost"] == round(1000 * DEPOSIT_RATE / 100 / 12, 1)
    assert r["deposit_converted_cost"] == round(50 + 5 + r["deposit_opportunity_cost"], 1)


def test_전세_매물은_정기예금_금리를_넘겨도_전환율을_그대로_쓴다():
    r = listing_recommender._to_result(
        listing(lease_type="전세", deposit=20000), 30, "카카오 API", RATE, 10000, DEPOSIT_RATE)
    assert r["deposit_opportunity_rate_percent"] == RATE
    assert r["deposit_opportunity_cost"] == round(20000 * RATE / 100 / 12, 1)
    assert r["loan_interest"] == round(10000 * RATE / 100 / 12)  # 부족분 대출이자도 전환율


def test_정기예금_금리를_안_넘기면_월세도_기존_전환율을_쓴다():
    r = result(current_deposit=0, lease_type="월세", deposit=1000, monthly_rent=50)
    assert r["deposit_opportunity_rate_percent"] == RATE


def test_순위_기준값도_월세만_정기예금_금리를_쓴다():
    wolse = listing(lease_type="월세", deposit=1000, monthly_rent=50, maintenance_fee=5)
    jeonse = listing(lease_type="전세", deposit=20000, maintenance_fee=5)
    assert listing_recommender._rank_cost(wolse, RATE, DEPOSIT_RATE) == round(55 + 1000 * DEPOSIT_RATE / 100 / 12, 1)
    assert listing_recommender._rank_cost(jeonse, RATE, DEPOSIT_RATE) == round(5 + 20000 * RATE / 100 / 12, 1)


# --- 정기예금(1년) 금리 조회 모듈 (R-ONE 응답은 가짜로 대체) ---
from app.services import deposit_interest_rate  # noqa: E402


def _reset_rate_module(monkeypatch, tmp_path):
    monkeypatch.setattr(deposit_interest_rate, "_memory", None)
    monkeypatch.setattr(deposit_interest_rate, "_last_fail_at", 0.0)
    monkeypatch.setattr(deposit_interest_rate, "CACHE_FILE", tmp_path / "deposit_interest_rate.json")
    monkeypatch.setattr(deposit_interest_rate.settings, "reb_api_key", "TESTKEY", raising=False)


class _FakeResp:
    def __init__(self, body):
        self._body = body

    def raise_for_status(self):
        pass

    def json(self):
        return self._body


def _rows(*pairs):
    return {"SttsApiTblData": [{"head": [{"RESULT": {"CODE": "INFO-000"}}]}, {"row": [{"CLS_NM": n, "DTA_VAL": v} for n, v in pairs]}]}


def test_가장_최근_월의_정기예금_1년_행만_고른다(monkeypatch, tmp_path):
    _reset_rate_module(monkeypatch, tmp_path)
    no_data = {"RESULT": {"CODE": "INFO-200", "MESSAGE": "no data"}}
    calls = []

    def fake_get(url, params, timeout):
        calls.append(params["WRTTIME_IDTFR_ID"])
        return _FakeResp(no_data if len(calls) == 1 else _rows(("정기예금", 3.14), ("정기예금(1년)", 3.39), ("저축성수신", 3.21)))

    monkeypatch.setattr(deposit_interest_rate.requests, "get", fake_get)
    r = deposit_interest_rate.get_one_year_deposit_rate()
    assert r.rate_percent == 3.39 and r.is_fallback is False
    assert len(calls) == 2 and "정기예금(1년)" in r.label


def test_조회가_실패하면_기본값으로_대체하고_예외를_던지지_않는다(monkeypatch, tmp_path):
    _reset_rate_module(monkeypatch, tmp_path)

    def boom(url, params, timeout):
        raise RuntimeError("network down")

    monkeypatch.setattr(deposit_interest_rate.requests, "get", boom)
    r = deposit_interest_rate.get_one_year_deposit_rate()
    assert r.is_fallback is True
    assert r.rate_percent == deposit_interest_rate.DEFAULT_RATE_PERCENT
