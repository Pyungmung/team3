# -*- coding: utf-8 -*-
# [담당: 송귀성] 보증금이 너무 낮은(비현실적으로 많은) 더미 매물을 자치구별로 균일하게 솎아내기
"""
dummyhouse_(자치구영문).csv에서 보증금이 기준(기본 500만원) 미만인 매물만 골라, 그중 약 80%를 무작위로 삭제한다.

- 균일하게: 자치구마다 같은 비율(약 80%)을 지우고, 자치구 안에서도 약 500m 격자(gen_dummyhouses/refill과 같은 기준)마다
  같은 비율로 지운다 - 그래서 지운 뒤에도 지도 위 분포 모양이 그대로 유지되고 특정 동네만 비지 않는다.
  자치구 전체 삭제 건수는 정확히 round(후보 수 x 0.8)이고, 격자별 몫(후보 수 x 0.8)을 올림/내림으로 나눠 맞춘다.
- 보증금이 기준 이상인 매물과, 회원이 직접 등록한 매물(--protect-after 이후 등록월, 기본 202609 초과)은 건드리지 않는다.
- 살아남은 행은 원본 바이트 그대로 둔다(줄바꿈/BOM 포함). 파일명은 바꾸지 않는다(dummyhouse_(자치구영문).csv 규칙).
- 사용법 (customhouse-ai 폴더에서):
    python scripts/thin_low_deposit_dummyhouses.py --dry-run            # 지우지 않고 결과만 미리 보기
    python scripts/thin_low_deposit_dummyhouses.py                      # 실제로 삭제 (같은 --seed면 같은 결과)
"""
import argparse
import csv
import io
import math
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

DUMMY_DIR = Path(__file__).resolve().parents[2] / "docs" / "samples" / "dummyhouses"


def _int(text):
    try:
        return int(float(str(text).replace(",", "").strip()))
    except ValueError:
        return 0


def cell_of(lat, lon):
    """약 500m 격자 (refill_dummyhouses.cell_of와 같은 기준). 좌표가 없으면 None."""
    try:
        return (round(float(lat) / 0.0045), round(float(lon) / 0.0057))
    except (TypeError, ValueError):
        return None


def pick_victims(cands_by_cell: dict, ratio: float, rng: random.Random) -> set:
    """자치구 전체에서 정확히 round(후보 수 x ratio)건을 지우되, 격자마다 후보 수에 비례하게 나눠 지운다.
    격자별 몫(후보 수 x ratio)의 정수 부분은 무조건 지우고, 모자란 건수는 소수 부분이 큰 격자일수록 높은 확률로 한 건씩 더 지운다
    (Efraimidis-Spirakis 가중 무작위 추출) - 자치구별 삭제율이 정확히 ratio이고 격자별로도 편차가 1건 안쪽이다."""
    total = sum(len(v) for v in cands_by_cell.values())
    target = round(total * ratio)
    quota, fracs = {}, {}
    for cell, idxs in cands_by_cell.items():
        want = len(idxs) * ratio
        quota[cell] = int(math.floor(want))
        fracs[cell] = want - quota[cell]
    extra = target - sum(quota.values())
    # 소수 부분이 있는 격자 중에서 extra개를 가중 추출 (소수 부분이 0인 격자는 올림 후보가 아니다)
    ranked = sorted((c for c in fracs if fracs[c] > 0), key=lambda c: rng.random() ** (1.0 / fracs[c]), reverse=True)
    for cell in ranked[:max(extra, 0)]:
        quota[cell] += 1
    victims = set()
    for cell, idxs in cands_by_cell.items():
        victims.update(rng.sample(idxs, min(quota[cell], len(idxs))))
    return victims


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=str(DUMMY_DIR))
    ap.add_argument("--threshold", type=int, default=500, help="이 보증금(만원) 미만이 솎아낼 대상")
    ap.add_argument("--ratio", type=float, default=0.8, help="대상 중 삭제 비율")
    ap.add_argument("--seed", type=int, default=20261005)
    ap.add_argument("--protect-after", default="202609", help="이 등록월(YYYYMM)보다 늦은 매물은 회원 등록분이라 지우지 않는다")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    files = sorted(Path(args.dir).glob("dummyhouse_*.csv"))
    if not files:
        sys.exit(f"CSV를 찾지 못했습니다: {args.dir}")

    total_before = total_after = total_cand = total_del = 0
    print(f"{'파일':26}{'전체':>7}{'대상':>7}{'삭제':>7}{'삭제율':>8}{'남은 대상':>10}{'격자수(전→후)':>16}")
    for f in files:
        raw = f.read_bytes()
        lines = raw.splitlines(keepends=True)
        header, body = lines[0], lines[1:]
        cols = next(csv.reader(io.StringIO(header.decode("utf-8-sig"))))
        ix = {c: i for i, c in enumerate(cols)}

        cands_by_cell = defaultdict(list)
        all_cells_before, n_cand = set(), 0
        for i, line in enumerate(body):
            row = next(csv.reader(io.StringIO(line.decode("utf-8"))))
            cell = cell_of(row[ix["위도"]], row[ix["경도"]])
            all_cells_before.add(cell)
            month = row[ix["매물등록번호"]].split("-")[1]
            if _int(row[ix["보증금(만원)"]]) < args.threshold and month <= args.protect_after:
                cands_by_cell[cell].append(i)
                n_cand += 1

        victims = pick_victims(cands_by_cell, args.ratio, rng)
        kept = [line for i, line in enumerate(body) if i not in victims]

        cells_after = set()
        for line in kept:
            row = next(csv.reader(io.StringIO(line.decode("utf-8"))))
            cells_after.add(cell_of(row[ix["위도"]], row[ix["경도"]]))

        if not args.dry_run and victims:
            f.write_bytes(header + b"".join(kept))

        total_before += len(body); total_after += len(kept); total_cand += n_cand; total_del += len(victims)
        rate = len(victims) / n_cand if n_cand else 0
        print(f"{f.name:26}{len(body):>7}{n_cand:>7}{len(victims):>7}{rate:>8.1%}{n_cand - len(victims):>10}{len(all_cells_before):>8}→{len(cells_after):<6}")

    print(f"{'합계':26}{total_before:>7}{total_cand:>7}{total_del:>7}{total_del / total_cand:>8.1%}{total_cand - total_del:>10}")
    print(f"총 {total_before:,}건 -> {total_after:,}건" + (" (dry-run: 파일은 바꾸지 않았습니다)" if args.dry_run else " (저장 완료)"))


if __name__ == "__main__":
    main()
