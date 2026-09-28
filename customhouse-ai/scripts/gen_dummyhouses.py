# -*- coding: utf-8 -*-
# [담당: 송귀성] 더미 매물 CSV 생성기
"""
더미 매물 CSV 생성기 (docs/samples/dummyhouses/dummyhouse_(자치구영문).csv)

- 국토부 전월세 실거래가 API(4종)의 실제 계약 1건을 매물 1건으로 삼는다 (가격은 소폭 변형).
- 도로명주소/우편번호/건물관리번호 등은 행안부 도로명주소 검색 API, 동명은 상세주소 API,
  좌표는 카카오 로컬 API로 채운다 (app/services/listing_builder.py). 나머지(관리비/주차/중개사/설명 등)는 더미.
- 지역 쏠림 완화: 건물당 상한(BUILDING_CAP) / 500m 격자 상한(CELL_CAP_RATIO) / 동별 균등+실거래 비중 혼합 쿼터.
- 재개 가능: 외부 API 결과는 scripts/_work/dummyhouse_cache.json에 저장하고, 끝난 자치구는 dummyhouse_done.json에 기록한다.
  (_work 폴더는 .gitignore 대상)
- 사용법 (customhouse-ai 폴더에서):
    python scripts/gen_dummyhouses.py [--districts seocho,gangnam] [--n 30] [--outdir <경로>]
  기본 계획: 서초구 4000건 + 서초구에서 가까운 순으로 24개 구 각 3000건. 결과는 settings.dummy_houses_dir에 저장한다.
"""
import argparse
import csv
import json
import math
import random
import shutil
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # customhouse-ai/
sys.path.insert(0, str(ROOT))
from app.core.config import settings  # noqa: E402
from app.services import listing_builder as builder  # noqa: E402
from app.services.listing_schema import (  # noqa: E402
    DISTRICT_ENG as ENG, LISTING_COLUMNS as COLUMNS, PHOTO_PLACEHOLDER, SEMI_JEONSE_RATIO, is_realistic_price,
)

WORK_DIR = Path(__file__).parent / "_work"
WORK_DIR.mkdir(exist_ok=True)
CACHE_FILE = WORK_DIR / "dummyhouse_cache.json"
DONE_FILE = WORK_DIR / "dummyhouse_done.json"
LOG_FILE = WORK_DIR / "dummyhouse_gen.log"
SCRATCH = WORK_DIR  # 임시 CSV(_tmp_*.csv)를 두는 곳
OUT_DIR_DEFAULT = Path(settings.dummy_houses_dir)
PROJECT = ROOT.parent  # team3/

MONTHS_FIRST = 6
MONTHS_WIDE = 12

_log_lock = threading.Lock()


def log(msg):
    line = time.strftime("%H:%M:%S ") + msg
    with _log_lock:
        with LOG_FILE.open("a", encoding="utf-8") as f:
            f.write(line + "\n")


builder.set_log_hook(log)

Transient = builder.Transient
resolve_key = builder.resolve_address
save_cache = builder.save_cache


def fetch_records(gu, lawd, months):
    return builder.fetch_molit_records(gu, lawd, months)


def load_cache():
    builder.configure_cache(CACHE_FILE)


# ---------------------------------------------------------------- 더미 값 생성
SURNAMES = list("김이박최정강조윤장임한오서신권황안송류홍")
GIVEN = ["도윤", "서연", "민준", "지우", "하준", "서윤", "예준", "지민", "시우", "유진", "현우", "수빈", "준서", "채원",
         "지호", "은서", "건우", "다은", "승현", "나연"]
ADJ = ["바로", "믿음", "한빛", "다온", "미래", "우리집", "든든", "해오름", "푸른", "새봄", "으뜸", "온누리"]
OPTIONS = ["에어컨", "냉장고", "세탁기", "인덕션", "붙박이장", "전자레인지", "책상", "침대", "신발장", "도어락"]


def round_to(v, unit):
    return max(unit, int(round(v / unit)) * unit)


def make_offices(gu, lawd, rng, road_pool):
    short = gu[:-1] if len(gu) > 2 else gu
    offices = []
    for i, adj in enumerate(ADJ):
        base = rng.choice(road_pool) if road_pool else f"서울특별시 {gu}"
        base = base.split(" (")[0]
        offices.append({
            "name": f"{short}{adj}공인중개사사무소",
            "rep": rng.choice(SURNAMES) + rng.choice(GIVEN),
            "reg": f"{lawd}-{rng.randint(2014, 2025)}-{rng.randint(1, 999):05d}",
            "tel": f"02-555-{100 + i * 7 + rng.randint(0, 6):04d}",
            "addr": f"{base} 1층",
        })
    return offices


def make_row(seq, gu, eng, lawd, rec, info, addr_source, rng, offices, force_rent=None):
    t = rec["type"]
    dep = rec["deposit"]
    rent = rec["rent"]
    # 실거래 가격에 소폭 변형을 준 매물 가격이 현실적인 범위인지(listing_schema.is_realistic_price) 확인하고,
    # 몇 번 다시 뽑아도 안 되면 이 실거래는 매물로 만들지 않는다(None).
    for _ in range(8):
        ldep = round_to(dep * rng.uniform(0.92, 1.12), 50 if dep < 1000 else (100 if dep < 10000 else 500)) if dep > 0 else 0
        if force_rent is not None:  # 월세 금액을 직접 지정(예: 21~24만원대 매물 추가). 실거래 월세 대비 ±12% 안에서만 쓴다.
            lrent = force_rent
        else:
            lrent = 0 if rent == 0 else max(1, round_to(rent * rng.uniform(0.9, 1.12), 1 if rent < 20 else 5))
        lease = "전세" if lrent == 0 else "월세"
        if is_realistic_price(lease, ldep, lrent):
            break
    else:
        return None
    ratio = f"{ldep / lrent:.1f}" if lrent > 0 else ""
    semi = "Y" if (lrent > 0 and ldep / lrent >= SEMI_JEONSE_RATIO) else "N"

    area = rec["area"]
    n = rng.randint(1, 8)
    floor_s = rec["floor"]
    floor_i = int(floor_s) if floor_s.lstrip("-").isdigit() else None
    if t == "아파트":
        ho = f"{rng.randint(101, 112)}동 {floor_i or 1}{n:02d}호"
    elif t == "오피스텔":
        ho = f"{floor_i or 1}{n:02d}호"
    elif t == "연립다세대":
        ho = f"{floor_i or 1}층 {floor_i or 1}{n:02d}호"
    else:  # 단독다가구: 국토부에 층 정보가 없음
        if rec["houseType"] == "단독":
            ho, floor_s = "단독주택 전체", ""
        else:
            fl = rng.randint(1, 3)
            ho, floor_s = f"{fl}층 {fl}{n:02d}호", str(fl)

    fee_rng = {"아파트": (10, 30), "오피스텔": (5, 15), "연립다세대": (2, 8), "단독다가구": (0, 5)}[t]
    fee = rng.randint(*fee_rng)
    fee_items = "없음" if fee == 0 else rng.choice(
        ["인터넷,수도,공용전기,청소", "공용전기,청소,승강기", "수도,공용전기", "인터넷,공용전기,청소,경비", "청소,승강기,공용전기"])

    if area <= 20:
        rooms, baths = 1, 1
    elif area <= 33:
        rooms, baths = rng.choice([1, 2]), 1
    elif area <= 60:
        rooms, baths = 2, rng.choice([1, 1, 2])
    elif area <= 85:
        rooms, baths = 3, 2
    else:
        rooms, baths = rng.choice([3, 4]), 2

    parking = {
        "아파트": ["가능(세대당 1대, 무료)", "가능(세대당 1.2대)", "가능(세대당 1대, 무료)"],
        "오피스텔": ["가능(세대당 1대, 월 5만원)", "가능(월 7만원)", "불가", "가능(월 5만원)"],
        "연립다세대": ["가능(1대)", "불가", "가능(월 3만원)", "가능(1대, 무료)"],
        "단독다가구": ["가능(골목 주차)", "불가", "가능(1대)"],
    }[t]
    parking_s = rng.choice(parking)
    elevator = "Y" if t in ("아파트", "오피스텔") else ("Y" if (floor_i or 1) >= 4 and rng.random() < 0.7 else "N")
    move_in = (date(2026, 9, 30) + timedelta(days=rng.randint(0, 81))).isoformat()
    regd = (date(2026, 9, 1) + timedelta(days=rng.randint(0, 27))).isoformat()
    status = rng.choices(["계약가능", "계약중"], weights=[95, 5])[0]

    bname = info["bdNm"] or rec["name"] or f"{rec['dong']} {rec['houseType'] or t}"
    if addr_source != "국토부 지번→행안부":
        bname = f"{rec['dong']} {rec['houseType'] or t}"
    opts = ", ".join(rng.sample(OPTIONS, rng.randint(3, 5)))
    kind = f"{lease} 매물" if lease == "월세" else "전세 매물"
    desc_by_type = {
        "아파트": f"{bname} {area:.1f}㎡ {kind}입니다. 단지 내 주차와 커뮤니티 시설을 이용할 수 있고 생활 편의시설이 가까워요. {rng.choice(['확장형 구조로 실사용 공간이 넉넉합니다.', '남향 배치로 채광이 좋습니다.', '올수리 상태로 바로 입주 가능합니다.'])}",
        "오피스텔": f"{bname} {area:.1f}㎡ {kind}입니다. 풀옵션({opts})으로 몸만 들어오시면 되고 보안 출입 시스템을 갖췄어요. {rng.choice(['대중교통 이용이 편리한 위치입니다.', '주변에 편의점·식당이 많아 생활이 편합니다.', '채광이 좋고 조용한 편입니다.'])}",
        "연립다세대": f"{rec['dong']} {area:.1f}㎡ 다세대 {kind}입니다. {rng.choice(['관리 상태가 깨끗하고 층간 소음이 적은 편입니다.', '도배·장판이 최근에 정리돼 있어요.', '골목 안쪽이라 조용합니다.'])} 옵션: {opts}.",
        "단독다가구": f"{rec['dong']} {rec['houseType'] or '주택'} {kind}입니다. 전용 약 {area:.1f}㎡, {rng.choice(['집주인 직접 관리하는 건물입니다.', '개별 난방이라 관리비 부담이 적어요.', '조용한 주택가에 위치해 있습니다.'])} 옵션: {opts}.",
    }
    office = offices[(sum(map(ord, rec["dong"])) + rng.randint(0, 2)) % len(offices)]
    agent_desc = rng.choice([
        "직접 방문 확인한 매물입니다. 도배·장판 상태와 옵션 작동 여부를 함께 점검해 드려요.",
        "임대인과 직접 연락이 되는 매물이라 계약 일정 조율이 빠릅니다. 등기부 확인도 같이 도와드릴게요.",
        "입주 전 하자 점검을 도와드립니다. 대출 가능 여부는 은행 확인이 필요하니 미리 문의 주세요.",
        "현장 사진과 실제 상태가 같은 매물입니다. 방문 예약 주시면 바로 안내해 드립니다.",
    ])

    rec_lease = "전세" if rent == 0 else "월세"
    return {
        "매물등록번호": f"{eng.upper()}-202609-{seq:04d}", "매물상태": status, "등록일": regd,
        "자치구": gu, "법정동": rec["dong"], "건물명": bname, "매물유형": t,
        "도로명주소": info["roadAddr"], "지번주소": info["jibunAddr"],
        "동호수_또는_단독층수": ho, "층": floor_s, "위도": info["lat"], "경도": info["lon"],
        "우편번호": info["zipNo"], "건축년도": rec["buildYear"],
        "거래유형": lease, "반전세여부": semi, "보증금÷월세_비율": ratio,
        "반전세판별기준": f"보증금÷월세 ≥ {SEMI_JEONSE_RATIO}",
        "보증금(만원)": ldep, "월세(만원)": lrent, "관리비(만원)": fee, "관리비포함항목": fee_items,
        "전용면적(㎡)": area, "방수": rooms, "욕실수": baths, "주차가능여부": parking_s, "엘리베이터": elevator,
        "이사가능일": move_in, "내부사진": PHOTO_PLACEHOLDER, "상세설명": desc_by_type[t],
        "공인중개사_상호": office["name"], "공인중개사_대표": office["rep"], "공인중개사_등록번호": office["reg"],
        "공인중개사_전화": office["tel"], "공인중개사_주소": office["addr"], "공인중개사설명": agent_desc,
        "참고_국토부_계약일": rec["date"], "참고_국토부_계약구분": rec["contractType"],
        "참고_국토부_계약기간": rec["contractTerm"], "참고_국토부_갱신요구권사용": rec["useRR"],
        "참고_국토부_종전보증금(만원)": rec["preDeposit"], "참고_국토부_종전월세(만원)": rec["preRent"],
        "참고_국토부_보증금(만원)": dep, "참고_국토부_월세(만원)": rent,
        "참고_국토부_전월세판별": f"{rec_lease} (monthlyRent={rent}, {'0이면 전세' if rent == 0 else '0보다 크면 월세'})",
        "참고_국토부_전용면적(㎡)": area, "참고_국토부_층": rec["floor"], "참고_국토부_지번": rec["jibun"],
        "참고_행안부_건물관리번호": info["bdMgtSn"], "참고_행안부_행정구역코드": info["admCd"],
        "참고_행안부_도로명코드": info["rnMgtSn"], "참고_행안부_상세주소_동명": info["dongNm"],
        "주소출처": addr_source,
    }


# ---------------------------------------------------------------- 자치구 1개 생성
SRC_OK = "국토부 지번→행안부"
SRC_BORROWED = "동 내 임의 도로명주소(단독다가구는 국토부에 지번 없음)"


POOL_FACTOR = 2.5       # 후보 풀 = 목표 건수 × 2.5
BUILDING_CAP = 8        # 한 건물(도로명주소)당 매물 상한. 후보가 모자라면 4씩 완화
DONG_UNIFORM_WEIGHT = 0.5   # 동별 배분 = 균등 50% + 실거래 비중 50%
CELL_CAP_RATIO = 0.03       # 500m 격자 한 칸이 자치구 매물의 3%를 넘지 않게. 후보가 모자라면 ×1.3씩 완화


def select_balanced(gu, pool, n, rng):
    """후보 풀에서 n건을 고른다. 건물 쏠림(상한)과 동 쏠림(균등+실거래 비중 혼합 쿼터)을 완화한다."""
    if len(pool) <= n:
        return pool

    def bkey(item):
        return (item[0]["dong"], item[1].get("roadAddr") or item[0]["jibun"] or item[0]["name"])

    # 1) 건물당 상한: 합계가 n을 못 채우면 상한을 완화
    def ckey(item):  # 약 500m 격자
        try:
            return (round(float(item[1]["lat"]) / 0.0045), round(float(item[1]["lon"]) / 0.0057))
        except (TypeError, ValueError, KeyError):
            return None

    cap, cell_cap = BUILDING_CAP, max(1, math.ceil(n * CELL_CAP_RATIO))
    while True:
        per_b, per_c, capped = {}, {}, []
        for it in pool:
            k, c = bkey(it), ckey(it)
            if per_b.get(k, 0) >= cap or (c is not None and per_c.get(c, 0) >= cell_cap):
                continue
            per_b[k] = per_b.get(k, 0) + 1
            if c is not None:
                per_c[c] = per_c.get(c, 0) + 1
            capped.append(it)
        if len(capped) >= n or len(capped) == len(pool):
            break
        cap += 4
        cell_cap = math.ceil(cell_cap * 1.3)

    # 2) 동별 쿼터(물 채우기): 균등 몫과 실거래 비중을 섞고, 후보가 모자란 동의 남는 몫은 다른 동에 재배분
    by_dong = {}
    for it in capped:
        by_dong.setdefault(it[0]["dong"], []).append(it)
    supply = {d: len(v) for d, v in by_dong.items()}
    total = sum(supply.values())
    weight = {d: DONG_UNIFORM_WEIGHT / len(supply) + (1 - DONG_UNIFORM_WEIGHT) * supply[d] / total for d in supply}
    quota, active, remaining = {}, set(supply), n
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
    picked = []
    for d, v in by_dong.items():
        picked.extend(v[: quota.get(d, 0)])
    # 정수 내림으로 모자란 몫은 남은 후보에서 무작위로 채운다
    if len(picked) < n:
        ids = {id(x) for x in picked}
        rest = [x for x in capped if id(x) not in ids]
        rng.shuffle(rest)
        picked.extend(rest[: n - len(picked)])
    rng.shuffle(picked)
    dcount = {}
    for it in picked:
        dcount[it[0]["dong"]] = dcount.get(it[0]["dong"], 0) + 1
    log(f"[{gu}] 균형 선택: 풀 {len(pool)} → 건물상한 {cap} 적용 {len(capped)} → {len(picked)}건 | 동별 " +
        ", ".join(f"{d} {c}" for d, c in sorted(dcount.items(), key=lambda x: -x[1])))
    return picked


def build_pool(gu, lawd, n, pool_target, rng, record_filter=None):
    """국토부 실거래를 모아 행안부/카카오로 주소·좌표를 붙인 후보 풀 [(rec, info, source)]을 만든다.
    n은 records를 12개월로 넓힐지 정하는 기준(목표 건수), pool_target은 풀 크기. records가 이미 섞여 있어 앞부분도 무작위 표본이다."""
    records = fetch_records(gu, lawd, MONTHS_FIRST)
    if len(records) < n * 2.0:
        records = fetch_records(gu, lawd, MONTHS_WIDE)
    if record_filter:  # 예: 월세 18~27만원대 실거래만 후보로
        records = [r for r in records if record_filter(r)]
    rng.shuffle(records)
    log(f"[{gu}] 목표 {n}건, 후보 {len(records)}건")

    rows_src = []          # (rec, info, source)
    dong_pool = {}         # 동 -> 해석된 연립다세대 info 목록 (단독다가구 주소 차용용)
    cursor = 0
    while len(rows_src) < pool_target and cursor < len(records):
        chunk = records[cursor: cursor + 500]
        cursor += len(chunk)
        normal = [r for r in chunk if r["type"] != "단독다가구"]
        solo = [r for r in chunk if r["type"] == "단독다가구"]

        keys = sorted({(r["dong"], r["jibun"]) for r in normal})

        def one(k):
            try:
                return k, resolve_key(gu, k[0], k[1]), None
            except Transient as e:
                return k, None, str(e)

        resolved, transient = {}, 0
        with ThreadPoolExecutor(max_workers=4) as pool:
            for k, info, err in pool.map(one, keys):
                if err:
                    transient += 1
                elif info:
                    resolved[k] = info
        save_cache(force=True)
        log(f"[{gu}] 청크 {cursor}/{len(records)}: 고유 지번 {len(keys)} 해석 {len(resolved)} 일시실패 {transient} (누적 {len(rows_src)}/{n})")

        for r in normal:
            info = resolved.get((r["dong"], r["jibun"]))
            if info:
                rows_src.append((r, info, SRC_OK))
                if r["type"] == "연립다세대":
                    dong_pool.setdefault(r["dong"], []).append(info)
        for r in solo:
            pool_infos = dong_pool.get(r["dong"])
            if pool_infos:
                rows_src.append((r, rng.choice(pool_infos), SRC_BORROWED))

    return rows_src


def generate(gu, lawd, n, outdir):
    eng = ENG[gu]
    rng = random.Random(f"dummyhouse-{gu}")
    pool_target = int(n * POOL_FACTOR)  # 고르게 뽑을 후보 풀
    rows_src = build_pool(gu, lawd, n, pool_target, rng)

    rng.shuffle(rows_src)  # 청크 안에서 유형별로 순서가 쏠려 있어서, 고르기 전에 섞는다
    rows_src = select_balanced(gu, rows_src, n, rng)
    if len(rows_src) < n:
        log(f"[{gu}] 경고: 목표 {n}건 중 {len(rows_src)}건만 생성 (후보 소진)")

    road_pool = [i["roadAddr"] for _, i, s in rows_src if s == SRC_OK]
    offices = make_offices(gu, lawd, rng, road_pool)
    rows = [make_row(i + 1, gu, eng, lawd, rec, info, src, rng, offices) for i, (rec, info, src) in enumerate(rows_src)]
    dropped = sum(1 for r in rows if r is None)
    if dropped:
        log(f"[{gu}] 비현실적 가격으로 제외 {dropped}건 (목표보다 그만큼 적게 생성)")
    rows = [r for r in rows if r is not None]

    tmp = SCRATCH / f"_tmp_{eng}.csv"
    with tmp.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(rows)
    target = outdir / f"dummyhouse_{eng}.csv"
    outdir.mkdir(parents=True, exist_ok=True)
    pending = None
    try:
        shutil.move(str(tmp), str(target))
    except PermissionError:
        # 엑셀 등에서 열려 있으면 임시 파일명으로 우회하지 않는다 - 생성 결과는 작업 폴더에 보관해 두고
        # 다른 자치구를 먼저 진행한 뒤, 맨 끝에서 열려 있는 파일이 닫히길 기다리며 다시 옮긴다.
        pending = (tmp, target)
        log(f"[{gu}] {target.name} 잠김(엑셀에서 열려 있을 수 있음) - 마지막에 다시 옮김 (보관: {tmp.name})")

    kinds = {}
    for r in rows:
        kinds[r["거래유형"]] = kinds.get(r["거래유형"], 0) + 1
    tag = "저장 대기(잠김)" if pending else "저장 완료"
    log(f"[{gu}] {tag} {target.name}: {len(rows)}건 | 거래유형 {kinds} | 반전세 {sum(1 for r in rows if r['반전세여부'] == 'Y')}건")
    return len(rows), pending


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--districts", default="")
    ap.add_argument("--n", type=int, default=0)
    ap.add_argument("--outdir", default="")
    args = ap.parse_args()

    lawd_map = json.loads((ROOT / "app/data/lawd_codes.json").read_text(encoding="utf-8"))["regions"]
    coords = json.loads((ROOT / "app/data/region_coords.json").read_text(encoding="utf-8"))["regions"]

    def hav(a, b):
        p1, p2 = math.radians(a[0]), math.radians(b[0])
        dl, dp = math.radians(b[1] - a[1]), p2 - p1
        x = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
        return 12742 * math.asin(math.sqrt(x))

    base = coords["서초구"]
    order = sorted((g for g in ENG if g != "서초구"), key=lambda g: hav(base, coords[g]))
    plan = [("서초구", 4000)] + [(g, 3000) for g in order]

    if args.districts:
        want = {d.strip() for d in args.districts.split(",")}
        plan = [(g, n) for g, n in plan if ENG[g] in want]
    if args.n:
        plan = [(g, args.n) for g, _ in plan]
    outdir = Path(args.outdir) if args.outdir else OUT_DIR_DEFAULT
    use_done = not args.districts and not args.n and not args.outdir

    load_cache()
    done = set(json.loads(DONE_FILE.read_text(encoding="utf-8"))) if (use_done and DONE_FILE.exists()) else set()
    log(f"=== 시작: 계획 {[(g, n) for g, n in plan]} / 이미 완료 {sorted(done)}")
    total = 0
    pendings = []
    for gu, n in plan:
        if gu in done:
            log(f"[{gu}] 이미 완료 - 건너뜀")
            continue
        t0 = time.time()
        try:
            count, pending = generate(gu, lawd_map[gu][0], n, outdir)
        except Exception as e:  # 한 구가 실패해도 다음 구를 계속 진행
            log(f"[{gu}] 실패: {type(e).__name__}: {e}")
            continue
        total += count
        if pending:
            pendings.append((gu, pending))
        elif use_done:
            done.add(gu)
            DONE_FILE.write_text(json.dumps(sorted(done), ensure_ascii=False), encoding="utf-8")
        log(f"[{gu}] 소요 {time.time() - t0:.0f}초")

    # 잠겨서 못 옮긴 파일은 열려 있는 프로그램이 닫힐 때까지 기다렸다가 옮긴다 (최대 약 2시간, 5분 간격)
    for attempt in range(24):
        if not pendings:
            break
        still = []
        for gu, (tmp, target) in pendings:
            try:
                shutil.move(str(tmp), str(target))
                log(f"[{gu}] 저장 완료(지연) {target.name}")
                if use_done:
                    done.add(gu)
                    DONE_FILE.write_text(json.dumps(sorted(done), ensure_ascii=False), encoding="utf-8")
            except PermissionError:
                still.append((gu, (tmp, target)))
        pendings = still
        if pendings:
            log(f"잠김 파일 대기 중 {[g for g, _ in pendings]} ({attempt + 1}/24) - 파일을 닫아주세요")
            time.sleep(300)
    for gu, (tmp, target) in pendings:
        log(f"[{gu}] 실패: {target.name} 끝까지 잠겨 있음 - 결과는 {tmp} 에 보관됨 (파일을 닫고 스크립트 재실행)")
    save_cache(force=True)
    log(f"=== 종료: 이번 실행 생성 {total}건, 완료 구 {sorted(done)}")


if __name__ == "__main__":
    main()
