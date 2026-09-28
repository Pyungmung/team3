# -*- coding: utf-8 -*-
# [담당: 송귀성] 비현실적 가격의 더미 매물 삭제 후 채우기
"""
이미 만들어 둔 dummyhouse_(자치구영문).csv에서 현실적으로 불가능한 가격의 매물을 지우고, 지운 만큼 같은 자치구
안에서 새 더미 매물로 다시 채운다 (자치구별 건수 유지: 서초구 4000건, 나머지 3000건).

가격 기준은 app/services/listing_schema.py의 is_realistic_price 한 곳에서 관리한다:
  - 월세/전세 모두 보증금 100만원 미만 삭제
  - 전세는 보증금 3000만원 미만 삭제
  - 월세는 (보증금 2000만원 이하 and 월세 20만원 이하) 삭제

- 살아남은 매물은 한 줄도 바꾸지 않는다. 새 매물은 매물등록번호를 이어서 붙이고, 중개사도 그 자치구의 기존 중개사를 쓴다.
- 새 매물은 gen_dummyhouses.py와 같은 방식(국토부 실거래 + 행안부 주소 + 카카오 좌표 + 소폭 가격 변형)으로 만들고,
  이미 있는 매물(삭제한 것 포함)과 같은 실거래는 다시 쓰지 않는다. 건물당 상한/격자 상한/동별 배분은 기존 매물까지 합쳐서 맞춘다.
- 사용법 (customhouse-ai 폴더에서):
    python scripts/refill_dummyhouses.py [--districts seocho,gangnam] [--outdir <경로>]
  --outdir을 주면 원본은 그대로 두고 그 폴더에 결과를 쓴다 (시험용).
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
import gen_dummyhouses as g  # noqa: E402  (후보 풀 생성/행 생성/캐시를 그대로 재사용)
from app.core.config import settings  # noqa: E402
from app.services import listing_schema as sc  # noqa: E402


def _int(text):
    try:
        return int(float(str(text).replace(",", "").strip()))
    except ValueError:
        return 0


def row_ok(row: dict) -> bool:
    return sc.is_realistic_price(row["거래유형"], _int(row["보증금(만원)"]), _int(row["월세(만원)"]))


def row_identity(row: dict) -> tuple:
    """CSV 행이 어떤 국토부 실거래에서 왔는지 (참고_국토부_* 컬럼 기준)."""
    return (row["매물유형"], row["법정동"], row["참고_국토부_지번"], str(row["참고_국토부_층"]).strip(),
            round(float(row["참고_국토부_전용면적(㎡)"] or 0), 3), row["참고_국토부_계약일"],
            _int(row["참고_국토부_보증금(만원)"]), _int(row["참고_국토부_월세(만원)"]))


def rec_identity(rec: dict) -> tuple:
    return (rec["type"], rec["dong"], rec["jibun"], str(rec["floor"]).strip(), round(float(rec["area"]), 3),
            rec["date"], int(rec["deposit"]), int(rec["rent"]))


def cell_of(lat, lon):
    return (round(float(lat) / 0.0045), round(float(lon) / 0.0057))  # 약 500m 격자 (select_balanced와 같은 기준)


def offices_from_rows(rows):
    seen = {}
    for r in rows:
        seen.setdefault(r["공인중개사_상호"], {"name": r["공인중개사_상호"], "rep": r["공인중개사_대표"], "reg": r["공인중개사_등록번호"],
                                          "tel": r["공인중개사_전화"], "addr": r["공인중개사_주소"]})
    return list(seen.values())


def dong_quotas(supply: dict, total: int) -> dict:
    """select_balanced와 같은 규칙: 균등 몫 + 실거래 비중 혼합 쿼터를 물 채우기로 배분 (지금 있는 매물 + 후보 기준)."""
    all_supply = sum(supply.values())
    weight = {d: g.DONG_UNIFORM_WEIGHT / len(supply) + (1 - g.DONG_UNIFORM_WEIGHT) * supply[d] / all_supply for d in supply}
    quota, active, remaining = {}, set(supply), total
    while active and remaining > 0:
        wsum = sum(weight[d] for d in active)
        full = [d for d in active if supply[d] <= remaining * weight[d] / wsum]
        if not full:
            for d in active:
                quota[d] = int(remaining * weight[d] / wsum)
            break
        for d in full:
            quota[d] = supply[d]
            remaining -= supply[d]
            active.discard(d)
    return quota


def fill_new_rows(gu, eng, lawd, kept, pool, need, target_n, rng, offices, max_seq, make=None, spread=False):
    """기존 매물(kept)에 더해 새 매물 need건을 후보 풀(pool)에서 고른다. 건물당 상한/격자 상한/동별 배분(균등+실거래 혼합)을
    기존 매물까지 합쳐서(총 target_n건 기준) 맞춘다. make(seq, rec, info, source) -> 행 또는 None(가격이 비현실적이면).
    spread=True면 동별 배분을 기존 매물과 무관하게 "새로 넣는 need건끼리" 균등+실거래 비중 혼합으로 나눈다 (소량을 위치가 고르게 추가할 때)."""
    removed = need
    if make is None:
        make = lambda seq, rec, info, source: g.make_row(seq, gu, eng, lawd, rec, info, source, rng, offices)  # noqa: E731
    per_b = Counter(r["도로명주소"] for r in kept)
    per_c = Counter(cell_of(r["위도"], r["경도"]) for r in kept)
    dong_have = Counter(r["법정동"] for r in kept)
    supply_pool = Counter(c[0]["dong"] for c in pool)
    supply = {d: dong_have[d] + supply_pool[d] for d in set(dong_have) | set(supply_pool)}
    if spread:
        deficit = dong_quotas({d: n for d, n in supply_pool.items() if n}, need)
    else:
        quota = dong_quotas(supply, target_n)
        deficit = {d: max(0, quota.get(d, 0) - dong_have[d]) for d in supply}

    new_rows, done = [], set()  # done: 이미 새 매물로 만들었거나 가격이 계속 비현실적이라 버린 후보 인덱스
    cap, cell_cap = g.BUILDING_CAP, max(1, math.ceil(target_n * g.CELL_CAP_RATIO))

    def try_add(idx):
        rec, info, source = pool[idx]
        cell = cell_of(info["lat"], info["lon"])
        if per_b[info["roadAddr"]] >= cap or per_c[cell] >= cell_cap:
            return False  # 건물/격자 상한 때문에 지금은 건너뜀 (done에 넣지 않아 상한이 풀리면 다시 본다)
        row = make(max_seq + len(new_rows) + 1, rec, info, source)
        done.add(idx)
        if row is None:  # 가격을 몇 번 다시 뽑아도 비현실적이면 이 실거래는 매물로 만들지 않는다
            return False
        new_rows.append(row)
        per_b[info["roadAddr"]] += 1
        per_c[cell] += 1
        return True

    # 1) 동별 부족분 순서대로 채운다 (부족한 동부터)
    by_dong = {}
    for i, c in enumerate(pool):
        by_dong.setdefault(c[0]["dong"], []).append(i)
    for d in sorted(deficit, key=lambda x: -deficit[x]):
        need_d = deficit[d]
        for i in by_dong.get(d, []):
            if need_d <= 0 or len(new_rows) >= removed:
                break
            if i not in done and try_add(i):
                need_d -= 1
    # 2) 아직 모자라면 (쿼터 계산 오차, 후보 부족, 가격 탈락) 남은 후보로 채우고, 그래도 안 되면 상한을 조금씩 푼다
    while len(new_rows) < removed:
        before = len(new_rows)
        for i in range(len(pool)):
            if len(new_rows) >= removed:
                break
            if i not in done:
                try_add(i)
        if len(new_rows) == before:
            if all(i in done for i in range(len(pool))) or cap > 60:
                break
            cap += 4
            cell_cap = math.ceil(cell_cap * 1.3)

    return new_rows


def refill_district(gu, src_dir: Path, out_dir: Path):
    eng = g.ENG[gu]
    src = src_dir / f"dummyhouse_{eng}.csv"
    with src.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    target_n = len(rows)
    kept = [r for r in rows if row_ok(r)]
    removed = target_n - len(kept)
    log = g.log
    if removed == 0:
        log(f"[{gu}] 삭제 대상 없음 - 그대로 유지 ({target_n}건)")
        return target_n, 0, 0

    rng = random.Random(f"dummyhouse-refill-{gu}")
    lawd = json.loads((g.ROOT / "app/data/lawd_codes.json").read_text(encoding="utf-8"))["regions"][gu][0]
    used_ids = {row_identity(r) for r in rows}  # 삭제한 매물의 실거래도 다시 쓰지 않는다
    seq_re = f"{eng.upper()}-202609-"
    max_seq = max((int(r["매물등록번호"].rsplit("-", 1)[1]) for r in rows if r["매물등록번호"].startswith(seq_re)), default=0)
    offices = offices_from_rows(kept)

    pool_target = max(removed * 12, 600)
    pool = g.build_pool(gu, lawd, target_n, pool_target, rng)
    pool = [c for c in pool if rec_identity(c[0]) not in used_ids]
    rng.shuffle(pool)
    log(f"[{gu}] 삭제 {removed}건 -> 새로 채울 후보 풀 {len(pool)}건 (기존 매물 {len(kept)}건 유지)")

    new_rows = fill_new_rows(gu, eng, lawd, kept, pool, removed, target_n, rng, offices, max_seq)

    if len(new_rows) < removed:
        log(f"[{gu}] 경고: {removed}건 중 {len(new_rows)}건만 채움 (후보 소진)")

    out_rows = kept + new_rows
    out_dir.mkdir(parents=True, exist_ok=True)
    tmp = g.SCRATCH / f"_refill_{eng}.csv"
    with tmp.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=sc.LISTING_COLUMNS)
        w.writeheader()
        w.writerows(out_rows)
    target = out_dir / f"dummyhouse_{eng}.csv"
    try:
        shutil.move(str(tmp), str(target))
    except PermissionError:
        log(f"[{gu}] 실패: {target.name} 잠김(엑셀 등에서 열려 있음) - 파일을 닫고 다시 실행하세요 (결과 보관: {tmp})")
        return target_n, removed, -1
    kinds = Counter(r["거래유형"] for r in out_rows)
    log(f"[{gu}] 저장 완료 {target.name}: {len(out_rows)}건 (삭제 {removed} / 신규 {len(new_rows)}) | 거래유형 {dict(kinds)}")
    return len(out_rows), removed, len(new_rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--districts", default="")
    ap.add_argument("--outdir", default="")
    args = ap.parse_args()
    src_dir = Path(settings.dummy_houses_dir)
    out_dir = Path(args.outdir) if args.outdir else src_dir
    want = {d.strip() for d in args.districts.split(",") if d.strip()}
    g.load_cache()
    g.log(f"=== 시작: 원본 {src_dir} -> 결과 {out_dir}")
    grand = Counter()
    for gu, eng in g.ENG.items():
        if want and eng not in want:
            continue
        t0 = time.time()
        try:
            n, removed, added = refill_district(gu, src_dir, out_dir)
        except Exception as e:  # noqa: BLE001 - 한 구가 실패해도 다음 구를 계속 진행
            g.log(f"[{gu}] 실패: {type(e).__name__}: {e}")
            continue
        grand.update({"removed": removed, "added": max(added, 0)})
        g.log(f"[{gu}] 소요 {time.time() - t0:.0f}초")
    g.log(f"=== 종료: 삭제 {grand['removed']}건 / 신규 {grand['added']}건")


if __name__ == "__main__":
    main()
