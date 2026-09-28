"""
[담당: 송귀성] 더미 매물 CSV 저장소 (docs/samples/dummyhouses/dummyhouse_(자치구영문).csv)

읽기(추천)와 쓰기(신규 매물 등록 탭용)를 한 곳에서 담당한다.
- 읽기: 자치구 파일을 처음 쓸 때 한 번 읽어 메모리에 두고, 파일이 바뀌면(mtime/크기) 자동으로 다시 읽는다.
- 쓰기: append_listing이 같은 54컬럼 스키마(listing_schema.py)로 한 행을 덧붙인다.
  엑셀 등에서 파일이 열려 있으면 ListingFileLockedError로 사용자에게 닫아 달라고 안내할 수 있게 한다.
"""
import csv
import logging
import re
import threading
from pathlib import Path

from app.core.config import settings
from app.services import listing_schema as schema

logger = logging.getLogger(__name__)

_lock = threading.RLock()  # append_listing이 락을 잡은 채 load_district/next_listing_id를 부르므로 재진입 가능해야 한다
_cache: dict[str, tuple[tuple[float, int], list[dict]]] = {}  # 자치구 -> ((mtime, size), 매물 목록)


class ListingFileLockedError(Exception):
    """CSV를 다른 프로그램(엑셀 등)이 열고 있어서 쓸 수 없을 때."""


def csv_path(region: str) -> Path:
    if region not in schema.DISTRICT_ENG:
        raise ValueError(f"지원하지 않는 자치구입니다: {region}")
    return Path(settings.dummy_houses_dir) / f"dummyhouse_{schema.DISTRICT_ENG[region]}.csv"


def available_districts() -> list[str]:
    """CSV 파일이 실제로 있는 자치구."""
    return [gu for gu in schema.DISTRICT_ENG if csv_path(gu).exists()]


def load_district(region: str) -> list[dict]:
    """자치구 매물 전체(내부 dict 목록). 파일이 없으면 빈 목록. 반환 목록은 공유 캐시이니 수정하지 말 것."""
    path = csv_path(region)
    try:
        stat = path.stat()
    except FileNotFoundError:
        return []
    signature = (stat.st_mtime, stat.st_size)

    with _lock:
        cached = _cache.get(region)
        if cached and cached[0] == signature:
            return cached[1]

    with path.open(encoding="utf-8-sig", newline="") as f:
        listings = [schema.row_to_listing(row) for row in csv.DictReader(f)]
    with _lock:
        _cache[region] = (signature, listings)
    logger.info(f"더미 매물 로드: {region} {len(listings)}건 ({path.name})")
    return listings


def iter_listings(regions: list[str] | None = None):
    """여러 자치구의 매물을 차례로 돌려준다 (기본: CSV가 있는 모든 자치구)."""
    for gu in regions if regions is not None else available_districts():
        yield from load_district(gu)


def get_listing(listing_id: str) -> dict | None:
    """매물등록번호(예: SEOCHO-202609-0001)로 1건 조회. 번호 앞부분으로 자치구 파일을 찾는다."""
    prefix = listing_id.split("-", 1)[0].lower()
    region = schema.ENG_DISTRICT.get(prefix)
    if region is None:
        return None
    return next((x for x in load_district(region) if x["listing_id"] == listing_id), None)


def next_listing_id(region: str) -> str:
    """해당 자치구의 다음 매물등록번호 (기존 최대 일련번호 + 1)."""
    pattern = re.compile(rf"^{schema.listing_id_prefix(region)}-\d{{6}}-(\d+)$")
    seqs = [int(m.group(1)) for x in load_district(region) if (m := pattern.match(x["listing_id"]))]
    return schema.new_listing_id(region, max(seqs, default=0) + 1)


def append_listing(region: str, listing: dict) -> dict:
    """신규 매물 1건을 자치구 CSV 끝에 덧붙인다. listing_id가 비어 있으면 자동 부여하고, 저장한 행을 돌려준다.
    (신규 매물 생성 탭에서 사용. 외부 API로 채운 주소·좌표·참고 실거래는 listing_builder.py 참고)"""
    path = csv_path(region)
    with _lock:  # 번호 부여부터 저장까지 한 구간 - 동시에 등록해도 매물등록번호가 겹치지 않게
        listing = {**listing, "region": region}
        if not listing.get("listing_id"):
            listing["listing_id"] = next_listing_id(region)
        if listing.get("is_semi_jeonse") is None:
            listing["is_semi_jeonse"] = schema.is_semi_jeonse(
                listing.get("deposit") or 0, listing.get("monthly_rent") or 0
            )
        listing.setdefault("listing_status", schema.LISTING_STATUS_AVAILABLE)
        listing.setdefault("photo", schema.PHOTO_PLACEHOLDER)
        row = schema.listing_to_row(listing)

        try:
            is_new = not path.exists()
            path.parent.mkdir(parents=True, exist_ok=True)
            # 새 파일만 BOM을 붙인다(엑셀 한글 호환). 기존 파일 뒤에는 BOM 없이 이어 쓴다.
            with path.open("w" if is_new else "a", encoding="utf-8-sig" if is_new else "utf-8", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=schema.LISTING_COLUMNS)
                if is_new:
                    writer.writeheader()
                writer.writerow(row)
        except PermissionError as e:
            raise ListingFileLockedError(
                f"{path.name} 파일을 다른 프로그램(엑셀 등)에서 열고 있어 저장할 수 없습니다. 파일을 닫고 다시 시도해 주세요."
            ) from e
        _cache.pop(region, None)
    return row
