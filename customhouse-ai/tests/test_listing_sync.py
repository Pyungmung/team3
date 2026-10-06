# [담당: 송귀성] 회원 등록 매물 DB 복원(listing_sync / listing_repository.upsert_rows)과 55컬럼 누락 방지 테스트
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402

from app.api.v1 import listing_registration  # noqa: E402
from app.services import listing_repository, listing_schema as schema, listing_sync  # noqa: E402


def row(listing_id, region="서초구", **kw):
    r = {h: "" for h in schema.LISTING_COLUMNS}
    r.update({"매물등록번호": listing_id, "자치구": region, "매물상태": "계약가능", "건물명": "복원매물", "보증금(만원)": 100, "월세(만원)": 21})
    r.update(kw)
    return r


@pytest.fixture()
def csv_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(listing_repository.settings, "dummy_houses_dir", str(tmp_path))
    listing_repository._cache.clear()
    yield tmp_path
    listing_repository._cache.clear()


def test_복원은_없는_매물만_자치구_CSV에_추가하고_여러_번_불러도_같다(csv_dir):
    rows = [row("SEOCHO-202610-0001"), row("SEOCHO-202610-0002"), row("GANGNAM-202610-0003", region="강남구")]
    assert listing_repository.upsert_rows(rows) == {"added": 3, "skipped": 0}
    assert {x["listing_id"] for x in listing_repository.load_district("서초구")} == {"SEOCHO-202610-0001", "SEOCHO-202610-0002"}
    assert [x["listing_id"] for x in listing_repository.load_district("강남구")] == ["GANGNAM-202610-0003"]
    assert listing_repository.upsert_rows(rows) == {"added": 0, "skipped": 3}      # 두 번째는 아무것도 추가하지 않는다
    assert len(listing_repository.load_district("서초구")) == 2


def test_이미_CSV에_있는_매물은_덮어쓰지_않고_자치구를_모르는_행은_건너뛴다(csv_dir):
    listing_repository.upsert_rows([row("SEOCHO-202610-0001", 건물명="원래이름")])
    result = listing_repository.upsert_rows([row("SEOCHO-202610-0001", 건물명="바뀐이름"), row("X-1", region="없는구"), {"자치구": "서초구"}])
    assert result == {"added": 0, "skipped": 3}
    assert listing_repository.get_listing("SEOCHO-202610-0001")["building_name"] == "원래이름"


def test_삭제된_상태의_매물도_그대로_복원된다(csv_dir):
    listing_repository.upsert_rows([row("SEOCHO-202610-0009", 매물상태="삭제됨")])
    assert listing_repository.get_listing("SEOCHO-202610-0009")["listing_status"] == "삭제됨"


def test_55컬럼이_저장_복원_왕복에서_모두_같다(csv_dir):
    full = {h: f"값{i}" for i, h in enumerate(schema.LISTING_COLUMNS)}
    full.update({"매물등록번호": "SEOCHO-202610-0077", "자치구": "서초구", "위도": "37.5", "경도": "127.0", "엘리베이터": "Y"})
    assert len(full) == 55
    listing_repository.upsert_rows([full])
    back = schema.listing_to_row(listing_repository.get_listing("SEOCHO-202610-0077"))
    for header in ("매물등록번호", "자치구", "건물명", "공인중개사_상호", "공인중개사_전화", "참고_행안부_건물관리번호", "주소출처", "엘리베이터"):
        assert str(back[header]) == full[header], header


def test_설정이_없으면_복원을_건너뛴다(monkeypatch):
    monkeypatch.setattr(listing_sync.settings, "backend_base_url", None)
    monkeypatch.setattr(listing_sync.settings, "internal_api_key", None)
    assert listing_sync.sync_once()["configured"] is False
    assert listing_sync.start_background_sync() is None
    assert listing_sync.sync_with_retry(sleep=lambda s: None) is False


def test_복원은_백엔드가_깨어날_때까지_재시도하고_성공하면_멈춘다(csv_dir, monkeypatch):
    monkeypatch.setattr(listing_sync.settings, "backend_base_url", "http://backend")
    monkeypatch.setattr(listing_sync.settings, "internal_api_key", "k")
    calls = []

    def fake_fetch():
        calls.append(1)
        if len(calls) < 3:
            raise ConnectionError("sleeping http://backend k")
        return [row("SEOCHO-202610-0100")]

    monkeypatch.setattr(listing_sync, "fetch_rows", fake_fetch)
    assert listing_sync.sync_with_retry(retries=5, interval=0, sleep=lambda s: None) is True
    assert len(calls) == 3 and listing_repository.get_listing("SEOCHO-202610-0100") is not None


def test_복원이_계속_실패해도_예외를_던지지_않는다(monkeypatch):
    monkeypatch.setattr(listing_sync.settings, "backend_base_url", "http://backend")
    monkeypatch.setattr(listing_sync.settings, "internal_api_key", "k")
    monkeypatch.setattr(listing_sync, "fetch_rows", lambda: (_ for _ in ()).throw(ConnectionError("down")))
    assert listing_sync.sync_with_retry(retries=3, interval=0, sleep=lambda s: None) is False


# ---- 55컬럼 누락 방지: 모든 CSV 컬럼은 "등록 입력 / 자동 채움 / 의도적 빈 값" 중 하나에 속해야 한다 ----

INTENTIONALLY_EMPTY = {  # 화면에서 실시간 조회하므로 등록 때 채우지 않는 국토부 참고 실거래 12컬럼
    "ref_contract_date", "ref_contract_type", "ref_contract_term", "ref_use_rr_right", "ref_pre_deposit",
    "ref_pre_monthly_rent", "ref_deposit", "ref_monthly_rent", "ref_lease_kind", "ref_area", "ref_floor", "ref_jibun",
}
AUTO_BY_REPOSITORY = {"listing_id", "listing_status", "registered_date", "is_semi_jeonse", "photo", "address_source"}


def test_모든_CSV_컬럼은_등록에서_채우거나_의도적으로_비우는_것이다():
    info = {"roadAddr": "서울특별시 서초구 동작대로 132", "jibunAddr": "서울특별시 서초구 방배동 1-1", "bdNm": "건물", "zipNo": "06700",
            "lat": 37.5, "lon": 127.0, "bdMgtSn": "B1", "admCd": "A1", "rnMgtSn": "R1", "dongNm": "가동"}
    req = listing_registration.ListingRegistrationRequest(
        addressKeyword="서울 서초구 동작대로 132", propertyType="오피스텔", leaseType="월세", deposit=100, monthlyRent=21, exclusiveArea=20,
        brokerName="상호", brokerRepresentative="대표", brokerRegNo="123", brokerPhone="02-1", brokerAddress="주소", brokerComment="설명")
    built = listing_registration._build_listing_fields(req, info, "서초구")
    keys = {key for _, key, _ in schema.LISTING_FIELDS}
    missing = keys - set(built) - AUTO_BY_REPOSITORY - INTENTIONALLY_EMPTY
    assert not missing, f"등록에서 채우지도 않고 의도적 빈 값 목록에도 없는 컬럼: {sorted(missing)}"
    assert built["broker_name"] == "상호" and built["broker_comment"] == "설명"
    assert (built["ref_bd_mgt_sn"], built["ref_adm_cd"], built["ref_rn_mgt_sn"], built["ref_detail_dong"]) == ("B1", "A1", "R1", "가동")
