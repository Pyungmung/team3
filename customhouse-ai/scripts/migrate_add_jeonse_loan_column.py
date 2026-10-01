"""
[담당: 송귀성] 1회성 마이그레이션 - 기존 더미 매물 CSV 25개에 "전세대출가능여부" 컬럼을 추가한다.
listing_schema.LISTING_FIELDS에 이미 컬럼을 추가해뒀으므로, 이 스크립트는 기존 행마다 그 값을 "Y"로
채워 넣고 전체 컬럼 순서를 지금 스키마 기준으로 다시 쓴다(새 컬럼 외 다른 값은 그대로 보존).

실행: customhouse-ai/.venv/Scripts/python.exe scripts/migrate_add_jeonse_loan_column.py
"""
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.services import listing_schema as schema  # noqa: E402
from app.core.config import settings  # noqa: E402

NEW_COLUMN = "전세대출가능여부"


def migrate_file(path: Path) -> int:
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        had_column = NEW_COLUMN in (reader.fieldnames or [])

    if had_column:
        print(f"  skip (이미 있음): {path.name}")
        return 0

    for row in rows:
        row[NEW_COLUMN] = "Y"

    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=schema.LISTING_COLUMNS)
        writer.writeheader()
        writer.writerows({col: row.get(col, "") for col in schema.LISTING_COLUMNS} for row in rows)

    print(f"  완료: {path.name} ({len(rows)}건)")
    return len(rows)


def main():
    base = Path(settings.dummy_houses_dir)
    files = sorted(base.glob("dummyhouse_*.csv"))
    print(f"대상 파일 {len(files)}개 (경로: {base})")
    total = 0
    for path in files:
        total += migrate_file(path)
    print(f"총 {total}건 마이그레이션 완료")


if __name__ == "__main__":
    main()
