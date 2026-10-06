"""
[담당: 송귀성] 더미 매물 CSV 저장소 (docs/samples/dummyhouses/dummyhouse_(자치구영문).csv)

읽기(추천)와 쓰기(신규 매물 등록 탭용)를 한 곳에서 담당한다.
- 읽기: 자치구 파일을 처음 쓸 때 한 번 읽어 메모리에 두고, 파일이 바뀌면(mtime/크기) 자동으로 다시 읽는다.
- 쓰기: append_listing이 같은 54컬럼 스키마(listing_schema.py)로 한 행을 덧붙인다.
  엑셀 등에서 파일이 열려 있으면 ListingFileLockedError로 사용자에게 닫아 달라고 안내할 수 있게 한다.
"""
import csv
import logging
import random
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
    """해당 자치구의 새 매물등록번호. 더미 매물(일련번호 순차 증가)과 구분되도록, 회원이 등록하는
    매물은 이번 달 안에서 쓰이지 않은 4자리를 무작위로 뽑는다(최대 50회 재시도 - 한 달에 9999건
    등록될 일은 없으니 사실상 항상 1~2번 안에 정해진다). 형식은 기존과 동일(listing_schema.new_listing_id)."""
    existing = {x["listing_id"] for x in load_district(region)}
    for _ in range(50):
        candidate = schema.new_listing_id(region, random.randint(0, 9999))
        if candidate not in existing:
            return candidate
    raise RuntimeError(f"{region} 매물번호를 50회 시도에도 생성하지 못했습니다(이번 달 번호가 거의 다 찼습니다).")


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
        listing.setdefault("jeonse_loan_available", True)
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


def upsert_rows(rows: list[dict]) -> dict:
    """DB에 저장돼 있던 회원 등록 매물(CSV 행 형태: 헤더 -> 값)을 CSV에 되살린다 (2026-10-06).
    서버가 새로 켜지면 CSV가 저장소의 원래 상태로 돌아와 등록 매물이 사라지므로, 켜질 때 백엔드 DB에서
    받아 온 행을 여기로 넣는다. 이미 CSV에 있는 매물번호는 건드리지 않아서(멱등) 여러 번 불러도 안전하고,
    자치구마다 파일을 한 번만 쓴다. 자치구를 알 수 없거나 지원하지 않는 행은 건너뛴다.
    반환: {"added": 새로 넣은 수, "skipped": 이미 있거나 건너뛴 수}"""
    by_region: dict[str, list[dict]] = {}
    skipped = 0
    for row in rows:
        region = (row.get("자치구") or "").strip()
        if not row.get("매물등록번호") or region not in schema.DISTRICT_ENG:
            skipped += 1
            continue
        by_region.setdefault(region, []).append(row)

    added = 0
    for region, region_rows in by_region.items():
        path = csv_path(region)
        with _lock:
            existing = {x["listing_id"] for x in load_district(region)}
            new_rows = []
            for row in region_rows:
                if row["매물등록번호"] in existing:
                    skipped += 1
                    continue
                existing.add(row["매물등록번호"])
                new_rows.append({header: row.get(header, "") for header in schema.LISTING_COLUMNS})
            if not new_rows:
                continue
            is_new = not path.exists()
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("w" if is_new else "a", encoding="utf-8-sig" if is_new else "utf-8", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=schema.LISTING_COLUMNS)
                if is_new:
                    writer.writeheader()
                writer.writerows(new_rows)
            _cache.pop(region, None)
            added += len(new_rows)
    return {"added": added, "skipped": skipped}


def update_listing(region: str, listing_id: str, updates: dict) -> dict | None:
    """매물 1건에 updates만 머지하고(건드리지 않은 필드·broker_*·ref_*·registered_date·listing_status는
    그대로 보존) CSV 전체를 다시 쓴다. 매물을 찾으면 머지된 행(내부 dict)을, 없으면 None을 돌려준다.
    update_listing_status와 같은 전체 재작성 패턴 - 매물 수정(PUT /api/v1/listings/{listing_id})이 쓴다."""
    path = csv_path(region)
    updates = {**updates, "is_semi_jeonse": schema.is_semi_jeonse(updates.get("deposit") or 0, updates.get("monthly_rent") or 0)}
    with _lock:
        listings = load_district(region)
        target = next((x for x in listings if x["listing_id"] == listing_id), None)
        if target is None:
            return None

        merged = {**target, **updates}
        rows = [schema.listing_to_row(merged if x is target else x) for x in listings]
        try:
            with path.open("w", encoding="utf-8-sig", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=schema.LISTING_COLUMNS)
                writer.writeheader()
                writer.writerows(rows)
        except PermissionError as e:
            raise ListingFileLockedError(
                f"{path.name} 파일을 다른 프로그램(엑셀 등)에서 열고 있어 저장할 수 없습니다. 파일을 닫고 다시 시도해 주세요."
            ) from e
        _cache.pop(region, None)
    return merged


def update_listing_status(region: str, listing_id: str, new_status: str) -> bool:
    """매물 1건의 상태(listing_status)만 바꿔 CSV 전체를 다시 쓴다. 매물을 찾으면 True, 없으면 False.
    "삭제"는 물리적으로 행을 지우는 대신 이 함수로 상태를 LISTING_STATUS_DELETED로 바꾼다 - 이미
    listing_recommender.py의 "계약가능이 아니면 추천 제외" 필터가 이 상태도 걸러내므로 추천 로직은
    손댈 필요가 없다(listing_recommender.py:300-301 참고)."""
    path = csv_path(region)
    with _lock:
        listings = load_district(region)
        target = next((x for x in listings if x["listing_id"] == listing_id), None)
        if target is None:
            return False

        rows = [
            schema.listing_to_row({**x, "listing_status": new_status} if x is target else x)
            for x in listings
        ]
        try:
            with path.open("w", encoding="utf-8-sig", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=schema.LISTING_COLUMNS)
                writer.writeheader()
                writer.writerows(rows)
        except PermissionError as e:
            raise ListingFileLockedError(
                f"{path.name} 파일을 다른 프로그램(엑셀 등)에서 열고 있어 저장할 수 없습니다. 파일을 닫고 다시 시도해 주세요."
            ) from e
        _cache.pop(region, None)
    return True
