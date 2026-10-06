"""
[담당: 송귀성] 메인 홈 미리보기 요약(listing_recommender.run_home_preview) 테스트 - 2026-10-06.
전체 진단과 같은 계산을 쓰되 요약 값 몇 개만 돌려준다: 적정 월세 상한/주거비 비율, 추천 지역(평균 통근시간 짧은 3곳), 예상 평균 통근시간(+범위).
실행 (customhouse-ai 폴더에서):  python -m pytest tests -q
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import listing_recommender  # noqa: E402
from tests.test_commute_estimate import at_km, listing, WORK  # noqa: E402


def preview(monkeypatch, listings, **extra):
    monkeypatch.setattr(listing_recommender.listing_repository, "load_district",
                        lambda region: listings if region == "강남구" else [])
    monkeypatch.setattr(listing_recommender.reb_conversion_rate, "get_metro_conversion_rate",
                        lambda: listing_recommender.reb_conversion_rate.ConversionRate(6.35, "2026-07", "전환율", False))
    monkeypatch.setattr(listing_recommender.deposit_interest_rate, "get_one_year_deposit_rate",
                        lambda: listing_recommender.deposit_interest_rate.DepositRate(3.39, "2026-08", "정기예금", False))
    req = DiagnosisRequest(annualIncome=3600, deposit=800, workLocation="강남구", workLat=WORK[0], workLon=WORK[1],
                           maxCommuteMinutes=40, preferredBuildingTypes=[], **extra)
    return listing_recommender.run_home_preview(req)


def test_홈_미리보기는_요약_값만_돌려준다(monkeypatch):
    out = preview(monkeypatch, [listing(f"L-{i}", 1 + i * 0.5) for i in range(6)])
    assert set(out) == {"affordable_rent", "rent_to_income_ratio", "regions", "avg_commute_minutes", "commute_range"}
    assert out["affordable_rent"] > 0 and out["rent_to_income_ratio"] > 0
    assert out["regions"] == ["강남구"]                       # 추천 매물이 3건 이상인 자치구만
    assert 10 <= out["avg_commute_minutes"] <= 40
    lo, hi = out["commute_range"].replace("분", "").split("~")
    assert int(lo) <= out["avg_commute_minutes"] <= int(hi)


def test_추천_매물이_없으면_평균_통근시간과_범위는_비어_있다(monkeypatch):
    out = preview(monkeypatch, [])
    assert out["regions"] == [] and out["avg_commute_minutes"] is None and out["commute_range"] is None
    assert out["affordable_rent"] > 0                          # 적정 월세 상한은 매물과 상관없이 소득으로 계산된다


def test_홈_미리보기는_보증금_필터를_모두_표시로_두고_대출_판별을_하지_않는다(monkeypatch):
    # 보유 보증금(800)을 넘는 매물도 포함된다 - 대충 보여주는 용도라 대출 자격 판별(비싼 계산)을 건너뛴다
    out = preview(monkeypatch, [listing(f"H-{i}", 1 + i * 0.4) | {"deposit": 5000} for i in range(5)], desiredDeposit=10000)
    assert out["avg_commute_minutes"] is not None
