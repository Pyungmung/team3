"""
[담당: 송귀성] 광고하기 매물(listing_recommender._build_ad_cards / run_listing_diagnosis의 ad_* 응답) 테스트 - 2026-10-08.
  - 광고는 직장 자치구 + 이 매물 자신의 직선거리 추정 통근시간 + 보증금 한도 + 계약가능 조건을 통과한 것만 나온다.
  - 일반 추천에 이미 나온 매물은 광고에서 뺀다 (같은 매물이 두 번 보이지 않게). 월세/전세는 따로 담는다.
  - 광고 매물번호가 없으면 응답이 기존과 같다(ad_* 빈 목록). 광고 카드에는 is_ad=True.
실행 (customhouse-ai 폴더에서):  python -m pytest tests -q
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.models.response_schema import ListingDiagnosisResponse  # noqa: E402
from app.services import listing_recommender  # noqa: E402


def listing(listing_id, lease_type, deposit, monthly_rent=0, region="강남구", lat=37.5005, lon=127.0365, status="계약가능"):
    return {
        "region": region, "property_type": "아파트", "building_name": "테스트", "dong": "역삼동",
        "road_address": f"서울시 {region}", "jibun_address": "", "lat": lat, "lon": lon,
        "exclusive_area": 30.0, "floor": "3", "lease_type": lease_type, "is_semi_jeonse": False,
        "monthly_rent": monthly_rent, "deposit": deposit, "maintenance_fee": 5,
        "listing_id": listing_id, "listing_status": status, "registered_date": "2026-09-01",
        "photo": "", "unit_label": "", "maintenance_fee_items": "", "parking": "", "elevator": None,
        "rooms": None, "bathrooms": None, "built_year": None, "move_in_date": "", "description": "",
        "jeonse_loan_available": True,
        "broker_name": "", "broker_representative": "", "broker_reg_no": "", "broker_phone": "", "broker_address": "", "broker_comment": "",
        "postal_code": "", "address_source": "",
    }


NORMAL = [listing("N-WOLSE", "월세", 500, 40), listing("N-JEONSE", "전세", 500)]
ADS = {
    "AD-WOLSE": listing("AD-WOLSE", "월세", 600, 50),
    "AD-JEONSE": listing("AD-JEONSE", "전세", 700),
    "AD-OTHER-REGION": listing("AD-OTHER-REGION", "월세", 600, 50, region="서초구"),                  # 직장 자치구가 아님
    "AD-TOO-FAR": listing("AD-TOO-FAR", "월세", 600, 50, lat=37.65, lon=127.35),                      # 통근시간 초과
    "AD-TOO-EXPENSIVE": listing("AD-TOO-EXPENSIVE", "전세", 99999),                                   # 보증금 한도 초과
    "AD-CONTRACTED": listing("AD-CONTRACTED", "월세", 600, 50, status="계약중"),                       # 계약가능이 아님
    "AD-DELETED": listing("AD-DELETED", "월세", 600, 50, status="삭제됨"),
    "AD-NO-COORD": listing("AD-NO-COORD", "월세", 600, 50, lat=None, lon=None),
    "N-WOLSE": NORMAL[0],                                                                              # 이미 일반 추천에 있는 매물
}


def run(monkeypatch, ad_ids, **kw):
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district",
                        lambda region: NORMAL if region == "강남구" else [])
    monkeypatch.setattr(listing_recommender.listing_repository, "get_listing", lambda listing_id: ADS.get(listing_id))
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    body = dict(annualIncome=3000, deposit=800, desiredDeposit=10000, workLocation="강남구", workLat=37.5, workLon=127.03,
                maxCommuteMinutes=60, preferredBuildingTypes=[], adListingIds=ad_ids)
    body.update(kw)
    return listing_recommender.run_listing_diagnosis(DiagnosisRequest(**body))


def ids(out, key):
    return sorted(x["listing_id"] for x in out[key])


def test_광고_매물번호가_없으면_광고_목록은_비고_일반_추천은_그대로다(monkeypatch):
    out = run(monkeypatch, [])
    assert out["ad_wolse_recommendations"] == [] and out["ad_jeonse_recommendations"] == []
    assert ids(out, "wolse_recommendations") == ["N-WOLSE"] and ids(out, "jeonse_recommendations") == ["N-JEONSE"]


def test_조건을_통과한_광고만_월세_전세로_나뉘어_담기고_광고_표시가_붙는다(monkeypatch):
    out = run(monkeypatch, list(ADS))
    assert ids(out, "ad_wolse_recommendations") == ["AD-WOLSE"]
    assert ids(out, "ad_jeonse_recommendations") == ["AD-JEONSE"]
    assert all(c["is_ad"] is True for c in out["ad_wolse_recommendations"] + out["ad_jeonse_recommendations"])
    assert all(c.get("is_ad") is not True for c in out["wolse_recommendations"] + out["jeonse_recommendations"])
    # 일반 추천에 이미 있는 매물(N-WOLSE)은 광고에서 빠지고, 일반 추천 순위는 광고 때문에 달라지지 않는다
    assert "N-WOLSE" not in ids(out, "ad_wolse_recommendations")
    assert ids(out, "wolse_recommendations") == ["N-WOLSE"]


def test_광고_카드는_일반_카드와_같은_계산_결과를_담는다(monkeypatch):
    out = run(monkeypatch, ["AD-WOLSE"])
    card = out["ad_wolse_recommendations"][0]
    assert card["listing_monthly_rent"] == 50 and card["listing_deposit"] == 600
    assert card["commute_source"] and isinstance(card["commute_minutes"], int)
    assert card["deposit_shortfall"] == 0          # 보증금 800 보유 >= 600
    assert "eligible_loans" in card and "broker" in card


def test_보증금_한도를_낮추면_그_이상_보증금의_광고는_빠진다(monkeypatch):
    out = run(monkeypatch, ["AD-WOLSE", "AD-JEONSE"], desiredDeposit=650)
    assert ids(out, "ad_wolse_recommendations") == ["AD-WOLSE"] and ids(out, "ad_jeonse_recommendations") == []


def test_희망_통근시간을_줄이면_멀어진_광고는_빠진다(monkeypatch):
    far = dict(ADS)
    far["AD-WOLSE"] = listing("AD-WOLSE", "월세", 600, 50, lat=37.58, lon=127.12)   # 직장에서 약 11km
    monkeypatch.setitem(ADS, "AD-WOLSE", far["AD-WOLSE"])
    assert ids(run(monkeypatch, ["AD-WOLSE"], maxCommuteMinutes=60), "ad_wolse_recommendations") == ["AD-WOLSE"]
    assert ids(run(monkeypatch, ["AD-WOLSE"], maxCommuteMinutes=15), "ad_wolse_recommendations") == []


def test_같은_매물번호가_여러_번_와도_한_번만_담긴다(monkeypatch):
    out = run(monkeypatch, ["AD-WOLSE", "AD-WOLSE", "AD-WOLSE"])
    assert [c["listing_id"] for c in out["ad_wolse_recommendations"]] == ["AD-WOLSE"]


def test_광고_개수는_월세_전세_각각_최대치로_자르고_섞어서_담는다(monkeypatch):
    many = {f"AD-W{i}": listing(f"AD-W{i}", "월세", 600, 50) for i in range(listing_recommender.MAX_AD_CARDS + 20)}
    monkeypatch.setattr(listing_recommender, "MAX_AD_CARDS", 100)
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district", lambda region: [])
    monkeypatch.setattr(listing_recommender.listing_repository, "get_listing", lambda listing_id: many.get(listing_id))
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    request = DiagnosisRequest(annualIncome=3000, deposit=800, desiredDeposit=10000, workLocation="강남구", workLat=37.5, workLon=127.03,
                               maxCommuteMinutes=60, adListingIds=list(many))
    out = listing_recommender.run_listing_diagnosis(request)
    got = [c["listing_id"] for c in out["ad_wolse_recommendations"]]
    assert len(got) == 100 and len(set(got)) == 100
    assert got != sorted(got, key=lambda x: int(x.split("W")[1]))   # 입력 순서 그대로가 아니라 섞여 있다 (우연히 같을 확률은 무시할 수준)


def test_응답_모델이_광고_필드를_그대로_통과시킨다(monkeypatch):
    out = run(monkeypatch, ["AD-WOLSE"])
    response = ListingDiagnosisResponse(**out)
    assert response.ad_wolse_recommendations[0].is_ad is True
    assert response.wolse_recommendations[0].is_ad is False


def test_백엔드가_null로_보낸_광고_목록은_빈_목록으로_본다():
    r = DiagnosisRequest(annualIncome=3000, deposit=800, workLocation="강남구", adListingIds=None)
    assert r.ad_listing_ids == []


def test_홈_미리보기는_광고_카드를_만들지_않는다(monkeypatch):
    calls = []
    real = listing_recommender._build_ad_cards
    monkeypatch.setattr(listing_recommender, "_build_ad_cards", lambda *a, **k: calls.append(1) or real(*a, **k))
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district", lambda region: NORMAL if region == "강남구" else [])
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    listing_recommender.run_home_preview(DiagnosisRequest(annualIncome=3000, deposit=800, desiredDeposit=10000, workLocation="강남구",
                                                          workLat=37.5, workLon=127.03, maxCommuteMinutes=60, adListingIds=["AD-WOLSE"]))
    assert calls == []
