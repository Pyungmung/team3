"""
[담당: 송귀성] 예금은행 정기예금(1년) 금리 조회 (한국부동산원 R-ONE가 재게시하는 한국은행 통계)

월세 매물의 "예금 전환 이자기회비용"(listing_recommender.py)이 보증금을 월 비용으로 환산할 때 쓰는 이율이다.
"그 보증금을 은행 예금에 넣었다면 받았을 이자"라는 개념이라, 월세를 보증금으로 바꿀 때 쓰는 전월세 전환율
(reb_conversion_rate.py, 전세/대출 계산에는 계속 그 값을 쓴다)이 아니라 예금 금리를 쓴다 (2026-10-03).
한국은행 기준금리 자체는 현재 연동된 API(R-ONE/KOSIS)에 없어서(ECOS 별도 키 필요), 같은 한국은행 통계인
"예금은행 수신금리(신규취급액 기준) - 정기예금(1년)"으로 대신한다.

- 통계표: R-ONE Open API "예금은행가중평균금리-수신"(월간, STATBL_ID=T232023129584012), 구분(CLS_NM) "정기예금(1년)"
- 발표가 약 1~2개월 늦게 나온다. 이번 달부터 거꾸로 최대 MAX_MONTHS_BACK개월을 훑어 가장 최근 값을 쓴다.
- 조회 결과는 12시간 동안 메모리에 두고, 성공한 값은 파일로도 저장해 둔다.
- 조회가 실패하면(키 없음, 네트워크, 응답 오류) 이전에 성공한 값 -> 파일에 저장된 값 -> 기본값(DEFAULT_RATE_PERCENT) 순으로 대신 쓰고
  is_fallback=True로 알려서 화면이 "조회 실패로 대체값 사용"을 밝힌다. 실패 직후에는 FAIL_RETRY_SECONDS 동안 다시 호출하지 않는다.
- 인증키는 reb_conversion_rate와 같은 .env의 REB_API_KEY (config.py의 settings.reb_api_key).
"""
import json
import logging
import threading
import time
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

R_ONE_DATA_URL = "https://www.reb.or.kr/r-one/openapi/SttsApiTblData.do"
STATBL_ID = "T232023129584012"  # 예금은행가중평균금리-수신 (월)
CLASS_NAME = "정기예금(1년)"

DEFAULT_RATE_PERCENT = 3.39   # 2026-08 값. 조회도 실패하고 저장된 값도 없을 때만 쓴다
DEFAULT_BASE_MONTH = "2026-08"
CACHE_TTL_SECONDS = 12 * 3600
FAIL_RETRY_SECONDS = 600
MAX_MONTHS_BACK = 6
REQUEST_TIMEOUT_SECONDS = 8
CACHE_FILE = Path(settings.data_dir) / "cache" / "deposit_interest_rate.json"


@dataclass
class DepositRate:
    rate_percent: float   # 연 %, 예: 3.39
    base_month: str       # 통계 기준 월 "YYYY-MM"
    label: str            # 화면/응답에 보여줄 출처 문구
    is_fallback: bool     # True면 이번 조회 실패로 대체한 값


_lock = threading.Lock()
_memory: tuple[DepositRate, float] | None = None  # (마지막 성공 값, 조회 시각)
_last_fail_at = 0.0


def _label(base_month: str) -> str:
    return f"예금은행 {CLASS_NAME} 금리(한국은행 통계) {base_month}"


def _months_back(count: int):
    """이번 달부터 거꾸로 count개월의 (YYYYMM, YYYY-MM)."""
    today = date.today()
    year, month = today.year, today.month
    for _ in range(count):
        yield f"{year}{month:02d}", f"{year}-{month:02d}"
        month -= 1
        if month == 0:
            year, month = year - 1, 12


def _query_month(yyyymm: str) -> float | None:
    """해당 월의 정기예금(1년) 금리(%)를 돌려준다. 그 달 데이터가 아직 없으면 None, 오류면 예외."""
    r = requests.get(
        R_ONE_DATA_URL,
        params={
            "KEY": settings.reb_api_key, "Type": "json", "STATBL_ID": STATBL_ID, "DTACYCLE_CD": "MM",
            "WRTTIME_IDTFR_ID": yyyymm, "pIndex": 1, "pSize": 1000,
        },
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    r.raise_for_status()
    body = r.json()
    parts = body.get("SttsApiTblData")
    if parts is None:
        # 데이터가 없거나 오류일 때는 {"RESULT": {"CODE": "...", "MESSAGE": "..."}} 형태로 온다
        code = (body.get("RESULT") or {}).get("CODE", "")
        if code == "INFO-200":
            return None  # 해당 월 데이터 없음(아직 발표 전) - 정상 상황이라 다음(이전) 달을 본다
        raise RuntimeError(f"R-ONE 오류 응답: {json.dumps(body, ensure_ascii=False)[:120]}")
    head = next((p["head"] for p in parts if "head" in p), [])
    code = next((h["RESULT"]["CODE"] for h in head if "RESULT" in h), "")
    if code == "INFO-200":
        return None
    rows = [x for p in parts if "row" in p for x in p["row"]]
    for row in rows:
        if row.get("CLS_NM") == CLASS_NAME and row.get("DTA_VAL") is not None:
            return float(row["DTA_VAL"])
    return None


def _fetch_latest() -> DepositRate:
    if not settings.reb_api_key:
        raise RuntimeError("REB_API_KEY가 설정돼 있지 않습니다")
    for yyyymm, ym in _months_back(MAX_MONTHS_BACK):
        value = _query_month(yyyymm)
        if value is not None:
            return DepositRate(round(value, 2), ym, _label(ym), False)
    raise RuntimeError(f"최근 {MAX_MONTHS_BACK}개월 안에 {CLASS_NAME} 금리 데이터가 없습니다")


def _save_disk(rate: DepositRate) -> None:
    try:
        CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
        CACHE_FILE.write_text(json.dumps(asdict(rate), ensure_ascii=False), encoding="utf-8")
    except OSError as e:  # 저장 실패는 추천 동작에 영향이 없다
        logger.warning(f"예금금리 캐시 파일 저장 실패: {e}")


def _fallback() -> DepositRate:
    """조회에 실패했을 때 대신 쓸 값: 이전 성공 값 -> 파일에 저장된 값 -> 기본값."""
    if _memory:
        r = _memory[0]
        return DepositRate(r.rate_percent, r.base_month, _label(r.base_month) + " (이전 조회값)", True)
    try:
        saved = json.loads(CACHE_FILE.read_text(encoding="utf-8"))
        return DepositRate(float(saved["rate_percent"]), saved["base_month"], _label(saved["base_month"]) + " (저장된 값)", True)
    except (OSError, ValueError, KeyError):
        return DepositRate(DEFAULT_RATE_PERCENT, DEFAULT_BASE_MONTH, _label(DEFAULT_BASE_MONTH) + " (기본값)", True)


def get_one_year_deposit_rate() -> DepositRate:
    """예금은행 정기예금(1년) 금리(연 %). 12시간 캐시. 조회 실패 시 대체값(is_fallback=True)을 돌려주고 예외를 던지지 않는다."""
    global _memory, _last_fail_at
    with _lock:
        now = time.time()
        if _memory and now - _memory[1] < CACHE_TTL_SECONDS:
            return _memory[0]
        if now - _last_fail_at < FAIL_RETRY_SECONDS:
            return _fallback()
    try:
        rate = _fetch_latest()
    except Exception as e:  # noqa: BLE001 - 어떤 실패든 추천을 막지 않는다
        # requests 예외 문구에는 요청 주소(=인증키)가 들어갈 수 있어서 키를 가려서 남긴다
        detail = str(e).replace(settings.reb_api_key or "<NO-KEY>", "<KEY>")[:150]
        logger.warning(f"정기예금(1년) 금리 조회 실패, 대체값 사용: {type(e).__name__}: {detail}")
        with _lock:
            _last_fail_at = time.time()
            return _fallback()
    with _lock:
        _memory = (rate, time.time())
    _save_disk(rate)
    logger.info(f"정기예금(1년) 금리 갱신: {rate.rate_percent}% ({rate.base_month})")
    return rate
