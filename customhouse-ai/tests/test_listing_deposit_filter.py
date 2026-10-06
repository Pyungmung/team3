"""
[담당: 송귀성] 추천 매물의 보증금 필터(listing_recommender.run_listing_diagnosis) 테스트 - 2026-10-06.
  - 기본: 현재 보유 보증금 이하이거나, 초과해도 대출이 가능한 매물만 추천한다 (초과 + 대출 불가면 제외).
  - show_all_deposits=True: 최대 매물 보증금(희망 보증금, 없으면 보유 보증금) 이하는 대출 여부와 상관없이 모두 추천한다.
  - 월세 추천의 반전세(보증금/월세 >= 100)는 기본으로 포함되고, include_semi_jeonse=False일 때만 뺀다 (2026-10-06 기본값 변경).
실행 (customhouse-ai 폴더에서):  python -m pytest tests -q
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import listing_recommender, loan_matcher  # noqa: E402

OWN = 800  # 현재 보유 보증금(만원)


def listing(listing_id, lease_type, deposit, monthly_rent=0, semi=False, loan_ok=True, area=30.0):
    return {
        "region": "강남구", "property_type": "아파트", "building_name": "테스트", "dong": "역삼동",
        "road_address": "서울시 강남구", "jibun_address": "", "lat": 37.5005, "lon": 127.0365,
        "exclusive_area": area, "floor": "3", "lease_type": lease_type, "is_semi_jeonse": semi,
        "monthly_rent": monthly_rent, "deposit": deposit, "maintenance_fee": 5,
        "listing_id": listing_id, "listing_status": "계약가능", "registered_date": "2026-09-01",
        "photo": "", "unit_label": "", "maintenance_fee_items": "", "parking": "", "elevator": None,
        "rooms": None, "bathrooms": None, "built_year": None, "move_in_date": "", "description": "",
        "jeonse_loan_available": loan_ok,
        "broker_name": "", "broker_representative": "", "broker_reg_no": "", "broker_phone": "", "broker_address": "", "broker_comment": "",
        "postal_code": "", "address_source": "",
    }


LISTINGS = [
    listing("T-JEONSE-OWN", "전세", 500),                                     # 보유 보증금 이하 -> 항상
    listing("T-JEONSE-LOAN", "전세", 3000),                                   # 초과 + 전세 대출 가능 -> 기본에서도 추천
    listing("T-JEONSE-NOLOAN-FLAG", "전세", 3000, loan_ok=False),             # 초과 + 등록자가 전세대출 불가 표시 -> 기본에서 제외
    listing("T-JEONSE-NOLOAN-LIMIT", "전세", 9000),                           # 초과 + 대출 한도(매물 보증금 상한 5000) 밖 -> 기본에서 제외
    listing("T-WOLSE-OWN", "월세", 700, 40),                                  # 보유 이하
    listing("T-WOLSE-LOAN", "월세", 1200, 40),                                # 초과 + 월세대출(매물 보증금 상한 1500 이내) 가능
    listing("T-WOLSE-NOLOAN", "월세", 2500, 40),                              # 초과 + 월세대출 상한 밖 -> 기본에서 제외
    listing("T-WOLSE-SEMI", "월세", 700, 5, semi=True),                       # 반전세 -> 기본 포함, "반전세 포함"을 해제하면 제외
]

LOANS = [
    {"type": "GENERAL_BEOTIMMOK", "name": "[일반]", "leaseType": "전세", "maxListingDeposit": 5000},
    {"type": "YOUTH_MONTHLY_RENT", "name": "[월세]", "leaseType": "월세", "maxListingDeposit": 1500},
]


def run(monkeypatch, **kw):
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district",
                        lambda region: LISTINGS if region == "강남구" else [])
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    body = dict(annualIncome=3000, deposit=OWN, desiredDeposit=10000, workLocation="강남구", workLat=37.5, workLon=127.03,
                maxCommuteMinutes=60, preferredBuildingTypes=[], loanProducts=LOANS)
    body.update(kw)
    out = listing_recommender.run_listing_diagnosis(DiagnosisRequest(**body))
    ids = lambda key: sorted(x["listing_id"] for x in out[key])
    return out, ids("wolse_recommendations"), ids("jeonse_recommendations")


def test_기본은_보유_보증금_이하이거나_초과해도_대출이_되는_매물만_추천한다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch)
    assert jeonse == ["T-JEONSE-LOAN", "T-JEONSE-OWN"]
    assert wolse == ["T-WOLSE-LOAN", "T-WOLSE-OWN", "T-WOLSE-SEMI"]   # 반전세는 기본 포함
    assert out["excluded_no_loan"] == 3                       # 전세 2건(대출불가 표시, 한도 밖) + 월세 1건(한도 밖)
    assert out["excluded_semi_jeonse"] == 0
    assert out["own_deposit"] == OWN and out["show_all_deposits"] is False and out["include_semi_jeonse"] is True


def test_모두_표시를_체크하면_최대_매물_보증금_이하는_대출_여부와_상관없이_전부_추천한다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch, showAllDeposits=True)
    assert jeonse == ["T-JEONSE-LOAN", "T-JEONSE-NOLOAN-FLAG", "T-JEONSE-NOLOAN-LIMIT", "T-JEONSE-OWN"]
    assert wolse == ["T-WOLSE-LOAN", "T-WOLSE-NOLOAN", "T-WOLSE-OWN", "T-WOLSE-SEMI"]
    assert out["excluded_no_loan"] == 0 and out["show_all_deposits"] is True


def test_모두_표시여도_최대_매물_보증금을_넘는_매물은_뺀다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch, showAllDeposits=True, desiredDeposit=2000)
    assert jeonse == ["T-JEONSE-OWN"]                       # 3000/9000은 최대 매물 보증금(2000) 초과라 대출 가능 여부와 상관없이 제외
    assert wolse == ["T-WOLSE-LOAN", "T-WOLSE-OWN", "T-WOLSE-SEMI"]   # 월세는 2500만 제외 (1200, 700은 2000 이하)


def test_반전세_포함을_해제하면_월세_추천에서_반전세가_빠진다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch, includeSemiJeonse=False)
    assert "T-WOLSE-SEMI" not in wolse and out["excluded_semi_jeonse"] == 1 and out["include_semi_jeonse"] is False
    assert "T-WOLSE-LOAN" in wolse                          # 반전세가 아닌 월세는 그대로
    _, with_semi, _ = run(monkeypatch)                      # 기본값은 포함
    assert "T-WOLSE-SEMI" in with_semi


def test_정책_대출_활용을_끄면_대출이_안_되므로_보유_보증금_이하만_추천한다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch, useLoanPolicy=False)
    assert jeonse == ["T-JEONSE-OWN"] and wolse == ["T-WOLSE-OWN", "T-WOLSE-SEMI"]   # 반전세(700)도 보유 보증금 이하라 포함


def test_저장된_대출이_없으면_보유_보증금_이하만_추천한다(monkeypatch):
    out, wolse, jeonse = run(monkeypatch, loanProducts=[])
    assert jeonse == ["T-JEONSE-OWN"] and wolse == ["T-WOLSE-OWN", "T-WOLSE-SEMI"]


def test_백엔드가_null로_보낸_체크박스_값은_기본값_false로_본다():
    r = DiagnosisRequest(annualIncome=3000, deposit=800, workLocation="강남구", showAllDeposits=None, includeSemiJeonse=None)
    assert r.show_all_deposits is False and r.include_semi_jeonse is True   # 모두 표시: 해제, 반전세 포함: 체크가 기본
