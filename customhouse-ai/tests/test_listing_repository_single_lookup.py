# [담당: 송귀성] get_listing 은 자치구 전체를 메모리에 올리지 않고 한 건만 읽는다 (2026-10-12).
# 배경: 서로 다른 자치구 매물을 한꺼번에 조회하면 구마다 CSV 전체(약 13MB)가 올라가 무료 서버(512MB)가 메모리 초과로 죽었다.
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402

from app.services import listing_repository as repo  # noqa: E402
from app.services import listing_schema as schema  # noqa: E402


@pytest.fixture(autouse=True)
def _캐시_초기화():
    saved = dict(repo._cache)
    repo._cache.clear()
    yield
    repo._cache.clear()
    repo._cache.update(saved)


def _샘플_번호(region="서초구"):
    # 전체 목록을 읽어 한 건을 고른 뒤 캐시를 다시 비운다 (이후 get_listing 이 '안 올라와 있는' 상태에서 시작하도록)
    listings = repo.load_district(region)
    assert listings, "테스트용 더미 매물 CSV 가 있어야 한다"
    chosen = listings[len(listings) // 2]["listing_id"]
    repo._cache.clear()
    return chosen


def test_안_올라와_있는_자치구는_전체를_올리지_않고_한_건만_읽는다():
    listing_id = _샘플_번호()
    assert "서초구" not in repo._cache
    found = repo.get_listing(listing_id)
    assert found is not None and found["listing_id"] == listing_id
    assert "서초구" not in repo._cache, "한 건만 읽었으니 자치구 전체가 캐시에 올라가면 안 된다"


def test_한_건만_읽은_결과가_전체를_올려_찾은_결과와_같다():
    listing_id = _샘플_번호()
    single = repo.get_listing(listing_id)
    repo._cache.clear()
    full = next(x for x in repo.load_district("서초구") if x["listing_id"] == listing_id)
    assert single == full


def test_이미_올라와_있는_자치구는_메모리에서_찾는다(monkeypatch):
    listing_id = _샘플_번호()
    repo.load_district("서초구")  # 추천 등이 이미 올려 둔 상태
    assert "서초구" in repo._cache
    # 파일을 다시 훑지 않는다 - open 을 막아도 찾아야 한다
    monkeypatch.setattr(Path, "open", lambda *a, **k: (_ for _ in ()).throw(AssertionError("파일을 다시 열면 안 된다")))
    assert repo.get_listing(listing_id)["listing_id"] == listing_id


def test_없는_번호와_모르는_자치구는_None():
    assert repo.get_listing("SEOCHO-209901-9999") is None
    assert repo.get_listing("NOWHERE-202609-0001") is None
    assert "서초구" not in repo._cache


def test_여러_자치구를_연달아_조회해도_캐시가_늘지_않는다():
    ids = []
    for gu in list(schema.DISTRICT_ENG)[:5]:
        ids.append(_샘플_번호(gu))
    repo._cache.clear()
    for i in ids:
        assert repo.get_listing(i) is not None
    assert len(repo._cache) == 0
