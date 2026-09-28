# -*- coding: utf-8 -*-
# [담당: 송귀성] 월세 20~24만원대 더미 매물 추가
"""
원래 생성기는 월세를 20만원 이상이면 5만원 단위로 반올림해서 21~24만원 매물이 거의 없었고, 보증금 2000만원 이하의
20만원 이하 월세를 지우고 나니(refill_dummyhouses.py) 낮은 월세 구간이 전부 25만원으로 몰렸다. 그래서 자치구마다 월세
20~24만원대 매물을 조금씩 추가한다 (2026-09-28).

- 자치구당 45건(서초구 60건)을 추가한다 (기존 매물은 한 줄도 바꾸지 않는다). 21/22/23/24만원이 고르게 나오게 하고,
  20만원은 보증금 2000만원 초과일 때만 만든다 (listing_schema.is_realistic_price 기준).
- 가격은 국토부 실거래 월세가 18~27만원인 계약에서 ±12% 안으로만 정하고(실거래 대비 과한 변형 없음), 위치는 동별 균등+실거래
  혼합, 건물당/격자 상한으로 고르게 퍼뜨린다 (refill_dummyhouses.fill_new_rows의 spread 모드).
- 사용법 (customhouse-ai 폴더에서): python scripts/add_wolse_20s.py [--districts seocho,gangnam] [--outdir <경로>] [--per-district 45]
"""
import argparse
import csv
import json
import math
import random
import shutil
import sys
import time
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import gen_dummyhouses as g  # noqa: E402
import refill_dummyhouses as rf  # noqa: E402
from app.core.config import settings  # noqa: E402
from app.services import listing_schema as sc  # noqa: E402

RENT_CANDIDATES = (20, 21, 22, 23, 24)
RECORD_RENT_RANGE = (18, 27)  # 이 범위의 실거래 월세를 가진 계약만 후보 (±12% 변형으로 20~24가 나올 수 있는 범위)


def add_district(gu, src_dir: Path, out_dir: Path, per_district: int):
    eng = g.ENG[gu]
    need = per_district + (15 if gu == "서초구" else 0)
    with (src_dir / f"dummyhouse_{eng}.csv").open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    rng = random.Random(f"dummyhouse-wolse20s-{gu}")
    lawd = json.loads((g.ROOT / "app/data/lawd_codes.json").read_text(encoding="utf-8"))["regions"][gu][0]
    used_ids = {rf.row_identity(r) for r in rows}
    seq_re = f"{eng.upper()}-202609-"
    max_seq = max((int(r["매물등록번호"].rsplit("-", 1)[1]) for r in rows if r["매물등록번호"].startswith(seq_re)), default=0)
    offices = rf.offices_from_rows(rows)

    lo, hi = RECORD_RENT_RANGE
    pool = g.build_pool(gu, lawd, len(rows), max(need * 12, 600), rng, record_filter=lambda r: lo <= r["rent"] <= hi)
    pool = [c for c in pool if rf.rec_identity(c[0]) not in used_ids]
    rng.shuffle(pool)
    log = g.log
    log(f"[{gu}] 월세 20~24만원 {need}건 추가 - 후보 풀 {len(pool)}건")

    chosen = Counter()

    def make(seq, rec, info, source):
        r0 = rec["rent"]
        allowed = [t for t in RENT_CANDIDATES if math.ceil(r0 * 0.9) <= t <= math.floor(r0 * 1.12)]
        if rec["deposit"] <= 2100:
            allowed = [t for t in allowed if t > sc.CHEAP_WOLSE_MAX_RENT]  # 20만원은 보증금 2000만원 초과일 때만 가능
        if not allowed:
            return None
        least = min(chosen[t] for t in allowed)  # 21~24가 고르게 나오도록 지금까지 가장 적게 뽑힌 금액을 쓴다
        t = rng.choice([t for t in allowed if chosen[t] == least])
        row = g.make_row(seq, gu, eng, lawd, rec, info, source, rng, offices, force_rent=t)
        if row is not None:
            chosen[t] += 1
        return row

    new_rows = rf.fill_new_rows(gu, eng, lawd, rows, pool, need, len(rows) + need, rng, offices, max_seq, make=make, spread=True)
    if len(new_rows) < need:
        log(f"[{gu}] 경고: {need}건 중 {len(new_rows)}건만 추가 (후보 소진)")

    out_rows = rows + new_rows
    out_dir.mkdir(parents=True, exist_ok=True)
    tmp = g.SCRATCH / f"_add20s_{eng}.csv"
    with tmp.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=sc.LISTING_COLUMNS)
        w.writeheader()
        w.writerows(out_rows)
    target = out_dir / f"dummyhouse_{eng}.csv"
    try:
        shutil.move(str(tmp), str(target))
    except PermissionError:
        log(f"[{gu}] 실패: {target.name} 잠김(엑셀 등에서 열려 있음) - 파일을 닫고 다시 실행하세요 (결과 보관: {tmp})")
        return len(rows), 0
    dongs = Counter(r["법정동"] for r in new_rows)
    log(f"[{gu}] 저장 완료 {target.name}: {len(rows)} -> {len(out_rows)}건 (추가 {len(new_rows)}, 월세 {dict(sorted(Counter(int(r['월세(만원)']) for r in new_rows).items()))}) | 동 {len(dongs)}곳")
    return len(out_rows), len(new_rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--districts", default="")
    ap.add_argument("--outdir", default="")
    ap.add_argument("--per-district", type=int, default=45)
    args = ap.parse_args()
    src_dir = Path(settings.dummy_houses_dir)
    out_dir = Path(args.outdir) if args.outdir else src_dir
    want = {d.strip() for d in args.districts.split(",") if d.strip()}
    g.load_cache()
    g.log(f"=== 시작: 월세 20~24만원대 추가 / 원본 {src_dir} -> 결과 {out_dir}")
    total = 0
    for gu, eng in g.ENG.items():
        if want and eng not in want:
            continue
        t0 = time.time()
        try:
            _, added = add_district(gu, src_dir, out_dir, args.per_district)
        except Exception as e:  # noqa: BLE001
            g.log(f"[{gu}] 실패: {type(e).__name__}: {e}")
            continue
        total += added
        g.log(f"[{gu}] 소요 {time.time() - t0:.0f}초")
    g.log(f"=== 종료: 총 {total}건 추가")


if __name__ == "__main__":
    main()
