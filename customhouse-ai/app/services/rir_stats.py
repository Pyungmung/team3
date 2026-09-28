"""
[담당: 송귀성] 소득 대비 주택임대료 비율(RIR) 통계 (docs/RIR.csv)

국토교통부 「주거실태조사」의 "지역 및 소득수준별 소득 대비 주택임대료 비율(RIR)" 표(KOSIS)를 CSV로 받아 둔 파일을 읽는다.
  - 소득 대비 주택임대료 비율 = (월임대료 ÷ 월가구소득) x 100, 월임대료와 월가구소득은 중위값이며 보증금은 월세전환율로
    월세로 환산한 값이다. 월가구소득은 세금 등을 제외한 월평균 실수령액이다 (CSV 주석 참고).
  - 이 앱은 수도권만 다루므로 지역별 값은 "수도권"만 쓴다. 소득수준별은 하위(1-4분위)/중위(5-8분위)/상위(9-10분위) 세 구간이다.

CSV 형식(가장 오른쪽 연도 열이 최신 값):
    통계표명:,지역 및 소득수준별 ...
    단위:,%,
    ,,2024
    전체,,15.8
    지역별 5),수도권,18.4
    ...
    소득수준별 6),하위(1-4분위),18.3
새 연도 자료로 바꿀 때는 파일만 교체하면 된다 (연도 열이 늘어나도 가장 오른쪽 열을 쓴다). 파일이 바뀌면(mtime) 자동으로 다시 읽는다.
파일이 없거나 읽을 수 없으면 None을 돌려주고, 호출하는 쪽이 기본값 DEFAULT_RIR_PERCENT(소득의 20%)로 대신한다
(2026-09-28: 예전 기본값 30%에서 20%로 변경).
"""
import csv
import logging
import re
import threading
from dataclasses import dataclass, field
from pathlib import Path

from app.core.config import settings

logger = logging.getLogger(__name__)

INCOME_KEYS = {"하위": "low", "중위": "mid", "상위": "high"}

# RIR.csv를 불러오지 못했을 때 주거비 비율(RIR)과 적정 월세 계산에 대신 쓰는 기본값(%)
DEFAULT_RIR_PERCENT = 20.0


@dataclass
class RirIncomeLevel:
    key: str            # "low" | "mid" | "high"
    label: str          # 예: "하위(1-4분위)"
    rir_percent: float  # 예: 18.3


@dataclass
class RirStats:
    year: int
    source: str
    overall_percent: float | None
    metro_percent: float                      # 수도권
    income_levels: list[RirIncomeLevel] = field(default_factory=list)


_lock = threading.Lock()
_cache: tuple[tuple[float, int], RirStats | None] | None = None  # ((mtime, size), 파싱 결과)


def _strip_note(label: str) -> str:
    """"지역별 5)" -> "지역별" (표의 각주 번호를 뗀다)."""
    return re.sub(r"\s*\d+\)\s*$", "", label.strip())


def _parse(path: Path) -> RirStats | None:
    with path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.reader(f))

    year_col, year = None, None
    values: dict[tuple[str, str], float] = {}
    source = ""
    group = ""
    for row in rows:
        row = [c.strip() for c in row] + [""] * 3
        if row[0].startswith("출처"):
            source = row[1]
            continue
        # 연도 헤더 행: 앞 칸은 비고 숫자 4자리(연도)가 있는 행. 여러 해면 가장 오른쪽(최신) 열을 쓴다.
        if year is None and not row[0] and not row[1]:
            years = [(i, int(c)) for i, c in enumerate(row) if re.fullmatch(r"(19|20)\d{2}", c)]
            if years:
                year_col, year = years[-1]
                continue
        if year_col is None:
            continue
        if row[0]:
            group = _strip_note(row[0])
        item = row[1] or group
        try:
            values[(group, item)] = float(row[year_col])
        except (ValueError, IndexError):
            continue

    metro = values.get(("지역별", "수도권"))
    if metro is None or year is None:
        return None
    levels = []
    for (g, item), v in values.items():
        if g != "소득수준별":
            continue
        key = next((k for name, k in INCOME_KEYS.items() if item.startswith(name)), None)
        if key:
            levels.append(RirIncomeLevel(key, item, v))
    levels.sort(key=lambda l: ["low", "mid", "high"].index(l.key))
    return RirStats(year, source, values.get(("전체", "전체")), metro, levels)


def get_rir_stats() -> RirStats | None:
    """RIR 통계 (수도권 + 소득수준별). 파일이 없거나 형식이 맞지 않으면 None."""
    global _cache
    path = Path(settings.rir_csv_file)
    try:
        stat = path.stat()
    except OSError:
        logger.warning(f"RIR 통계 파일을 찾을 수 없습니다: {path}")
        return None
    signature = (stat.st_mtime, stat.st_size)
    with _lock:
        if _cache and _cache[0] == signature:
            return _cache[1]
    try:
        parsed = _parse(path)
    except (OSError, UnicodeDecodeError, csv.Error) as e:
        logger.warning(f"RIR 통계 파일을 읽지 못했습니다: {type(e).__name__}: {e}")
        parsed = None
    if parsed is None:
        logger.warning(f"RIR 통계 파일 형식을 인식하지 못했습니다 (수도권/연도 행 없음): {path.name}")
    else:
        logger.info(f"RIR 통계 로드: {parsed.year}년 수도권 {parsed.metro_percent}% / 소득수준별 {[(l.label, l.rir_percent) for l in parsed.income_levels]}")
    with _lock:
        _cache = (signature, parsed)
    return parsed
