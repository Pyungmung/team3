# -*- coding: utf-8 -*-
# [담당: 송귀성] 주거정책 CSV -> policies.json 변환
"""
docs/housing_policy_list.csv(사람이 편집하는 정책 목록)를 앱이 읽는 customhouse-ai/app/data/policies.json으로 변환한다.
CSV를 고칠 때마다 이 스크립트를 다시 실행해야 리포트의 "주거정책 추천"에 반영된다 (JSON은 CSV를 직접 읽지 않는다).

변환 규칙 (기존 JSON과 같은 규칙 - 2026-09-28 기존 JSON 23건이 그대로 재현되는 것을 확인):
  - 빈 줄은 건너뛰고, 나머지 행을 CSV 순서대로 POLICY_001, POLICY_002 ... 번호를 붙인다
  - 지역(region) = 1열 ("서울" 또는 자치구 이름), 기관명(agency) = 2열, 정책명(name) = 3열
  - 지원혜택(description) = 4열, 줄바꿈/연속 공백은 공백 한 칸으로 정리
  - 나이(min_age/max_age) = 5,6열, 연소득 상한(max_annual_income) = 7열, 총자산 상한(max_asset) = 8열, 기준중위소득 %(median_income_percent) = 9열
  - 기초수급자/중소기업/신혼부부/무주택 조건(require_*) = 10~13열 (1이면 true)
  - is_loan: 정책명에 "대출", "전세자금", "이자지원"이 들어가면 true (진단 폼에서 "정책 대출 활용"을 해제한 사용자에게 추천하지 않는 데 쓴다)
  - 14열(특이사항)은 JSON에 넣지 않는다
  - 15열(중위소득값, 1인가구 기준중위소득 100%의 월 금액, 원)이 비어 있지 않으면 median_income_100_percent_monthly_manwon(만원)으로 쓴다.
    없으면 기존 JSON 값을 유지한다.
사용법 (customhouse-ai 폴더에서):
    python scripts/convert_policies.py                # docs/housing_policy_list.csv -> app/data/policies.json 덮어쓰기
    python scripts/convert_policies.py --check        # 파일은 바꾸지 않고 결과만 보여준다
"""
import argparse
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # customhouse-ai/
sys.path.insert(0, str(ROOT))
from app.core.config import settings  # noqa: E402

CSV_PATH = ROOT.parent / "docs" / "housing_policy_list.csv"
LOAN_KEYWORDS = ("대출", "전세자금", "이자지원")


def _int(text):
    text = (text or "").replace(",", "").strip()
    if not text:
        return None
    try:
        return int(float(text))
    except ValueError:
        return None


def _flag(text):
    return (text or "").strip() == "1"


def _clean(text):
    return re.sub(r"\s+", " ", (text or "")).strip()


def convert(csv_path: Path):
    """CSV -> (정책 목록, 중위소득 100% 월 금액(만원) 또는 None)."""
    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.reader(f))
    policies, median_100 = [], None
    for row in rows[1:]:  # 첫 줄은 헤더
        row = [c for c in row] + [""] * 15
        if not any(c.strip() for c in row):
            continue  # 빈 줄
        name = _clean(row[2])
        policies.append({
            "id": f"POLICY_{len(policies) + 1:03d}",
            "region": _clean(row[0]),
            "agency": _clean(row[1]),
            "name": name,
            "description": _clean(row[3]),
            "min_age": _int(row[4]),
            "max_age": _int(row[5]),
            "max_annual_income": _int(row[6]),
            "max_asset": _int(row[7]),
            "median_income_percent": _int(row[8]),
            "require_basic_livelihood": _flag(row[9]),
            "require_sme": _flag(row[10]),
            "require_newlywed": _flag(row[11]),
            "require_no_household": _flag(row[12]),
            "is_loan": any(k in name for k in LOAN_KEYWORDS),
        })
        won = _int(row[14])
        if won and median_100 is None:
            median_100 = won / 10000  # 원 -> 만원
    return policies, median_100


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="파일을 바꾸지 않고 결과만 보여준다")
    args = ap.parse_args()

    policies, median_100 = convert(CSV_PATH)
    out_path = Path(settings.policies_file)
    existing = json.loads(out_path.read_text(encoding="utf-8")) if out_path.exists() else {}
    data = {
        "_owner": existing.get("_owner", "송귀성"),
        "_comment": existing.get("_comment", ""),
        "median_income_100_percent_monthly_manwon": median_100 if median_100 is not None
        else existing.get("median_income_100_percent_monthly_manwon"),
        "policies": policies,
    }
    if "scripts/convert_policies.py" not in data["_comment"]:
        data["_comment"] += " (변환은 customhouse-ai/scripts/convert_policies.py로 한다: python scripts/convert_policies.py)"

    regions = Counter(p["region"] for p in policies)
    print(f"정책 {len(policies)}건 | 지역 {len(regions)}곳 | 대출 상품 {sum(p['is_loan'] for p in policies)}건 | 중위소득 100% {data['median_income_100_percent_monthly_manwon']}만원")
    print("지역별:", dict(regions.most_common()))
    if args.check:
        return
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"저장 완료: {out_path}")


if __name__ == "__main__":
    main()
