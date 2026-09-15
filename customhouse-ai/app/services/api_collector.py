"""
[담당: 송귀성] 공공 API 수집기 - 국토교통부 아파트 전월세 실거래가

data.go.kr(공공데이터포털)의 "국토교통부_아파트 전월세 실거래가 자료" API를 호출해서
지역(법정동코드) × 월(YYYYMM) 단위 실거래 내역을 가져오고, calculator.py가 쓰는
"보증금 1000만원 기준 평균 월세"(base_rent) 형태로 요약한다.

엔드포인트/파라미터는 실제로 호출해서 확인했다 (더미 키로도 다음 응답이 오는 것까지 검증됨):
    GET https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent
        ?serviceKey=...&LAWD_CD=11680&DEAL_YMD=202401&type=json
    -> {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR", ...}}}
   (serviceKey가 유효하면 response.body.items.item 배열이 내려온다)

⚠️ 응답 필드명(아파트/보증금액/월세금액 등)은 이 API가 원래 한글 XML 태그를 쓰던 것을
JSON으로도 그대로 내려주는 방식으로 알려져 있으나, 실제 유효한 서비스키가 없어 이 세션에서는
JSON 응답 스키마까지는 직접 검증하지 못했다. 아래 파싱 함수는 문서화된 필드명 후보를 여러 개
시도하도록 방어적으로 작성했다 — 실제 키를 발급받아 첫 호출을 해보면 로그에 raw item이 그대로
찍히니(logger.debug), 다를 경우 FIELD_ALIASES만 고치면 된다.

서비스키 발급: https://www.data.go.kr → "아파트 전월세 실거래가" 검색 → 활용신청 (보통 즉시 승인)
              발급받은 "일반 인증키(Decoding)"를 backend가 아니라 customhouse-ai/.env의
              MOLIT_API_KEY 에 넣는다.
"""
import logging
from datetime import date
from functools import lru_cache
from statistics import mean

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

MOLIT_RENT_ENDPOINT = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
REQUEST_TIMEOUT_SEC = 5

# 실제 필드명이 다를 경우를 대비해 후보를 여러 개 둔다 (응답 dict에서 먼저 매칭되는 키를 쓴다).
FIELD_ALIASES = {
    "apt_name": ["아파트", "aptNm", "아파트명"],
    "deposit": ["보증금액", "deposit", "보증금"],
    "monthly_rent": ["월세금액", "monthlyRent", "월세"],
    "exclusive_area": ["전용면적", "excluUseAr"],
}


class MolitApiError(Exception):
    """국토교통부 API 호출/인증 실패 (호출부에서 잡아서 샘플 데이터로 폴백시킨다)."""


def is_enabled() -> bool:
    """MOLIT_API_KEY가 설정되어 있어야 실 API를 시도한다. 없으면 즉시 폴백."""
    return bool(settings.molit_api_key)


def _pick(item: dict, field: str):
    for key in FIELD_ALIASES[field]:
        if key in item:
            return item[key]
    return None


def _parse_amount(raw) -> float | None:
    """API가 " 12,345" 처럼 콤마/공백이 섞인 문자열로 금액을 내려주는 경우가 많아 안전하게 파싱."""
    if raw is None:
        return None
    try:
        return float(str(raw).replace(",", "").strip())
    except ValueError:
        return None


def fetch_recent_rent_items(lawd_cd: str, months_back: int = 2) -> list[dict]:
    """최근 N개월치 아파트 전월세 실거래 내역(raw item)을 모아서 반환한다."""
    if not is_enabled():
        raise MolitApiError("MOLIT_API_KEY가 설정되지 않았습니다.")

    items: list[dict] = []
    today = date.today()

    for offset in range(months_back):
        month = today.month - offset
        year = today.year
        while month <= 0:
            month += 12
            year -= 1
        deal_ymd = f"{year}{month:02d}"

        params = {
            "serviceKey": settings.molit_api_key,
            "LAWD_CD": lawd_cd,
            "DEAL_YMD": deal_ymd,
            "type": "json",
            "numOfRows": 200,
        }

        try:
            res = requests.get(MOLIT_RENT_ENDPOINT, params=params, timeout=REQUEST_TIMEOUT_SEC)
            res.raise_for_status()
            body = res.json()
        except (requests.RequestException, ValueError) as e:
            raise MolitApiError(f"국토교통부 API 호출 실패 (LAWD_CD={lawd_cd}, {deal_ymd}): {e}") from e

        header = body.get("OpenAPI_ServiceResponse", {}).get("cmmMsgHeader")
        if header:
            raise MolitApiError(f"국토교통부 API 인증/요청 오류: {header.get('errMsg')} - {header.get('returnAuthMsg')}")

        try:
            raw_items = body["response"]["body"]["items"]["item"]
        except (KeyError, TypeError):
            logger.debug("예상과 다른 응답 형식 (LAWD_CD=%s, %s): %s", lawd_cd, deal_ymd, body)
            continue

        if isinstance(raw_items, dict):  # 결과가 1건이면 배열이 아니라 dict로 내려오는 API가 많음
            raw_items = [raw_items]
        items.extend(raw_items)

    return items


@lru_cache(maxsize=64)
def summarize_region_rent(lawd_cd: str) -> dict | None:
    """
    실거래 내역을 calculator.py가 쓰는 형태로 요약한다.
    반환: {"base_rent": 만원, "sample_size": N} 또는 데이터가 없으면 None.

    calculator.py의 base_rent는 "보증금 1000만원 기준 월세"이므로, 실거래는 전세(월세 0원)도
    섞여있어 월세가 0보다 큰 거래만 골라 평균을 낸다 (전세 매물은 별도 지표라 여기선 제외).
    """
    try:
        items = fetch_recent_rent_items(lawd_cd)
    except MolitApiError as e:
        logger.warning(str(e))
        return None

    rents = []
    for item in items:
        monthly_rent = _parse_amount(_pick(item, "monthly_rent"))
        if monthly_rent and monthly_rent > 0:
            rents.append(monthly_rent)

    if not rents:
        return None

    return {"base_rent": round(mean(rents)), "sample_size": len(rents)}
