# [담당: 송귀성] 실거래 참고 API - 삭제된 매물 안내용 scope="deleted"
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.v1 import listing_reference  # noqa: E402


def test_없는_매물은_deleted로_알려준다(monkeypatch):
    monkeypatch.setattr(listing_reference.listing_repository, "get_listing", lambda listing_id: None)
    out = listing_reference.get_reference_transactions("NOPE-1")
    assert out.scope == "deleted" and out.transactions == []


def test_삭제_상태인_매물도_deleted로_알려준다(monkeypatch):
    # 삭제는 행을 지우지 않고 상태만 "삭제됨"으로 바꾼다 - get_listing이 돌려줘도 삭제로 취급해야 한다
    listing = {"region": "서초구", "property_type": "오피스텔", "dong": "방배동", "jibun_address": "", "listing_status": "삭제됨"}
    monkeypatch.setattr(listing_reference.listing_repository, "get_listing", lambda listing_id: listing)
    out = listing_reference.get_reference_transactions("SEOCHO-1")
    assert out.scope == "deleted" and out.transactions == []


def test_삭제_상태인_매물은_새로고침_카드를_만들지_않는다(monkeypatch):
    from app.services import listing_recommender
    monkeypatch.setattr(listing_recommender.listing_repository, "get_listing", lambda listing_id: {"listing_status": "삭제됨"})
    assert listing_recommender.refresh_listing_card(object(), "SEOCHO-1") is None


def test_있는_매물은_deleted가_아니다(monkeypatch):
    listing = {"region": "강남구", "property_type": "연립다세대", "dong": "개포동", "jibun_address": "", "ref_contract_date": "", "listing_status": "계약가능"}
    monkeypatch.setattr(listing_reference.listing_repository, "get_listing", lambda listing_id: listing)
    monkeypatch.setattr(listing_reference.api_collector, "is_enabled", lambda: False)
    out = listing_reference.get_reference_transactions("OK-1")
    assert out.scope == "none"
