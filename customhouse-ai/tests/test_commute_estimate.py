"""
[담당: 송귀성] 직선거리 통근시간 추정식(calculator.COMMUTE_ESTIMATE_PARAMS) 테스트 - 2026-10-06.
이동수단별로 카카오 실제 경로 시간을 대량 측정해 회귀한 평균 보정식이고, 검색/매칭의 "희망 통근시간" 필터가 이 추정치를 쓴다.
실행 (customhouse-ai 폴더에서):  python -m pytest tests -q
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import calculator, listing_recommender  # noqa: E402

WORK = (37.5, 127.03)
KM_PER_DEG_LAT = 111.19


def at_km(km):
    """직장에서 정북쪽으로 km만큼 떨어진 좌표."""
    return WORK[0] + km / KM_PER_DEG_LAT, WORK[1]


def est(km, transport=None):
    lat, lon = at_km(km)
    return calculator._estimate_commute_minutes_fallback(WORK[0], WORK[1], lat, lon, transport)


def test_이동수단별_추정식은_측정값_회귀식과_같다():
    assert est(5, "PUBLIC") == round(7.6 + 6.39 * 2 + 2.53 * 3)   # 2km까지 가파르고 이후 완만 = 28
    assert est(10, "CAR") == round(1.3 + 6.27 * 2 + 2.37 * 8)     # 33
    assert est(1, "WALK") == round(1.9 + 20.36 * 1)               # 22 (도보는 직선 하나)
    assert est(0, "WALK") == 2


def test_아주_가까운_매물도_실측_최소값_아래로는_내려가지_않는다():
    assert est(0, "PUBLIC") == 10 and est(0.1, "PUBLIC") == 10      # 대중교통은 가까워도 10분 안팎 (실측 최솟값)
    assert est(0.5, "PUBLIC") == 11 and est(1, "PUBLIC") == 14      # 실측 평균 11~12분 / 15분 부근 - 직선식(15.8분)보다 낮다
    assert est(0, "CAR") == 4


def test_이동수단을_모르거나_안_주면_대중교통_기준이다():
    assert est(6) == est(6, "PUBLIC") == est(6, "BIKE") == est(6, None)


def test_도보는_대중교통보다_거리에_훨씬_민감하다():
    assert est(3, "WALK") > est(3, "PUBLIC") > 0
    assert est(3, "WALK") - est(1, "WALK") > 35


def test_희망_통근시간을_최대_직선거리로_환산한다():
    for transport in ("PUBLIC", "CAR", "WALK"):
        km = calculator.max_commute_distance_km(30, transport)
        assert est(km, transport) in (29, 30, 31)             # 반올림 오차 이내 (꺾임 앞뒤 구간 모두 역함수가 맞는다)
    assert calculator.max_commute_distance_km(60, "PUBLIC") > calculator.max_commute_distance_km(30, "PUBLIC") > 2.0  # 2km 이후 구간
    assert calculator.max_commute_distance_km(5, "PUBLIC") == 0   # 기본분(7.6)보다 짧은 희망 시간이면 0km


# --- 추천 필터: 희망 통근시간을 넘는 추정치의 매물은 빠진다 ---
def listing(listing_id, km):
    lat, lon = at_km(km)
    return {
        "region": "강남구", "property_type": "아파트", "building_name": "테스트", "dong": "역삼동",
        "road_address": "서울시 강남구", "jibun_address": "", "lat": lat, "lon": lon,
        "exclusive_area": 30.0, "floor": "3", "lease_type": "월세", "is_semi_jeonse": False,
        "monthly_rent": 40, "deposit": 700, "maintenance_fee": 5,
        "listing_id": listing_id, "listing_status": "계약가능", "registered_date": "2026-09-01",
        "photo": "", "unit_label": "", "maintenance_fee_items": "", "parking": "", "elevator": None,
        "rooms": None, "bathrooms": None, "built_year": None, "move_in_date": "", "description": "",
        "jeonse_loan_available": True,
        "broker_name": "", "broker_representative": "", "broker_reg_no": "", "broker_phone": "", "broker_address": "", "broker_comment": "",
        "postal_code": "", "address_source": "",
    }


def recommend(monkeypatch, transport, max_minutes, listings):
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district",
                        lambda region: listings if region == "강남구" else [])
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    req = DiagnosisRequest(annualIncome=3000, deposit=800, workLocation="강남구", workLat=WORK[0], workLon=WORK[1],
                           maxCommuteMinutes=max_minutes, transportType=transport, preferredBuildingTypes=[])
    out = listing_recommender.run_listing_diagnosis(req)
    return {x["listing_id"]: x["commute_minutes"] for x in out["wolse_recommendations"]}


def test_대중교통_30분이면_직선_약_6km_이내만_추천하고_표시_시간도_30분_이하다(monkeypatch):
    got = recommend(monkeypatch, "PUBLIC", 30, [listing("NEAR-3KM", 3), listing("EDGE-5KM", 5.5), listing("FAR-8KM", 8), listing("FAR-12KM", 12)])
    assert set(got) == {"NEAR-3KM", "EDGE-5KM"}
    assert all(m <= 30 for m in got.values())


def test_도보_30분이면_직선_약_1_4km_이내만_추천한다(monkeypatch):
    got = recommend(monkeypatch, "WALK", 30, [listing("W-0.5KM", 0.5), listing("W-1.3KM", 1.3), listing("W-2KM", 2.0), listing("W-5KM", 5.0)])
    assert set(got) == {"W-0.5KM", "W-1.3KM"}


def test_자동차_30분은_대중교통보다_더_먼_매물까지_추천한다(monkeypatch):
    listings = [listing("N-4KM", 4), listing("N-7KM", 7), listing("N-9KM", 9)]
    car = recommend(monkeypatch, "CAR", 30, listings)
    public = recommend(monkeypatch, "PUBLIC", 30, listings)
    assert set(public) == {"N-4KM"} and set(car) == {"N-4KM", "N-7KM", "N-9KM"}
