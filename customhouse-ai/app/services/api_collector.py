"""
[담당: 송귀성] 공공 API 수집기 - 국토교통부 아파트 전월세 실거래가

data.go.kr(공공데이터포털)의 "국토교통부_아파트 전월세 실거래가 자료" API를 호출해서
지역(법정동코드) × 월(YYYYMM) 단위 실거래 내역을 가져오고, calculator.py가 쓰는
"보증금 1000만원 기준 평균 월세"(base_rent) 형태로 요약한다.

실제 발급받은 키로 호출해서 검증 완료 (2026-09-15):
    GET https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent
        ?serviceKey=...&LAWD_CD=11680&DEAL_YMD=202408&numOfRows=1

    ⚠️ &type=json 파라미터는 이 API에서 무시된다 — 정상 응답은 항상 XML로 온다.
       (인증/게이트웨이 오류 응답만 JSON으로 온다. 아래 _parse_response가 둘 다 처리한다.)

    정상 응답(XML) 예시:
        <response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
        <body><items><item>
          <aptNm>개포래미안포레스트</aptNm><buildYear>2020</buildYear>
          <dealYear>2024</dealYear><dealMonth>8</dealMonth><dealDay>31</dealDay>
          <deposit>124,000</deposit><monthlyRent>0</monthlyRent>
          <excluUseAr>84.9</excluUseAr><floor>5</floor><umdNm>개포동</umdNm>
        </item></items><numOfRows>1</numOfRows><pageNo>1</pageNo><totalCount>1613</totalCount>
        </body></response>
    (필드명은 camelCase 영문이다 — 한글 XML 태그를 쓰던 구버전 문서와 달랐다. deposit/monthlyRent는
     "만원" 단위 숫자를 콤마 포함 문자열로 내려준다. monthlyRent가 0이면 전세 거래.)

    인증 오류 응답(JSON) 예시:
        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR", ...}}}

서비스키 발급: https://www.data.go.kr → "아파트 전월세 실거래가" 검색 → 활용신청 (보통 즉시 승인)
              발급받은 "일반 인증키(Decoding)"를 backend가 아니라 customhouse-ai/.env의
              DATA_GO_KR_API_KEY 에 넣는다.
              같은 키를 한국주택금융공사(HF)·행정안전부 법정동코드 API에도 그대로 쓸 수 있다
              (data.go.kr은 계정당 인증키 1개로 여러 공공 API를 공용으로 쓰는 구조다).
"""
import json
import logging
import xml.etree.ElementTree as ET
from datetime import date
from functools import lru_cache

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

MOLIT_RENT_ENDPOINT = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
REQUEST_TIMEOUT_SEC = 5

# 실제 응답 필드명(camelCase)을 우선으로 두고, 혹시 다른 버전을 만날 경우를 대비해 한글 태그도 후보로 둔다.
FIELD_ALIASES = {
    "apt_name": ["aptNm", "아파트", "아파트명"],
    "deposit": ["deposit", "보증금액", "보증금"],
    "monthly_rent": ["monthlyRent", "월세금액", "월세"],
    "exclusive_area": ["excluUseAr", "전용면적"],
}


class MolitApiError(Exception):
    """국토교통부 API 호출/인증 실패 (호출부에서 잡아서 샘플 데이터로 폴백시킨다)."""


def is_enabled() -> bool:
    """DATA_GO_KR_API_KEY가 설정되어 있어야 실 API를 시도한다. 없으면 즉시 폴백."""
    return bool(settings.data_go_kr_api_key)


def _pick(item: dict, field: str):
    for key in FIELD_ALIASES[field]:
        if key in item:
            return item[key]
    return None


def _parse_amount(raw) -> float | None:
    """API가 "124,000"처럼 콤마/공백이 섞인 문자열로 금액을 내려주므로 안전하게 파싱."""
    if raw is None:
        return None
    try:
        return float(str(raw).replace(",", "").strip())
    except ValueError:
        return None


def _parse_response(text: str, lawd_cd: str, deal_ymd: str) -> list[dict]:
    """
    정상 응답(XML)이면 item 목록을 dict 리스트로 반환하고,
    인증/게이트웨이 오류 응답(JSON)이면 MolitApiError를 던진다.
    """
    stripped = text.lstrip()

    if stripped.startswith("{"):
        try:
            body = json.loads(text)
        except ValueError as e:
            raise MolitApiError(f"알 수 없는 응답 형식(LAWD_CD={lawd_cd}, {deal_ymd}): {text[:200]}") from e

        header = body.get("OpenAPI_ServiceResponse", {}).get("cmmMsgHeader")
        if header:
            raise MolitApiError(f"국토교통부 API 인증/요청 오류: {header.get('errMsg')} - {header.get('returnAuthMsg')}")
        raise MolitApiError(f"알 수 없는 JSON 응답(LAWD_CD={lawd_cd}, {deal_ymd}): {text[:200]}")

    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        raise MolitApiError(f"XML 파싱 실패(LAWD_CD={lawd_cd}, {deal_ymd}): {e}") from e

    result_code = root.findtext("header/resultCode")
    if result_code != "000":
        result_msg = root.findtext("header/resultMsg")
        raise MolitApiError(f"국토교통부 API 오류(LAWD_CD={lawd_cd}, {deal_ymd}): {result_code} - {result_msg}")

    return [
        {child.tag: (child.text or "").strip() for child in item_el}
        for item_el in root.findall("body/items/item")
    ]


def fetch_recent_rent_items(lawd_cd: str, months_back: int = 3) -> list[dict]:
    """최근 N개월치 아파트 전월세 실거래 내역(raw item)을 모아서 반환한다.

    (실거래 신고는 계약 후 30일 이내라, 당월 데이터는 아직 비어있을 수 있어 3개월치를 모은다.)
    """
    if not is_enabled():
        raise MolitApiError("DATA_GO_KR_API_KEY가 설정되지 않았습니다.")

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
            "serviceKey": settings.data_go_kr_api_key,
            "LAWD_CD": lawd_cd,
            "DEAL_YMD": deal_ymd,
            "numOfRows": 300,
        }

        try:
            res = requests.get(MOLIT_RENT_ENDPOINT, params=params, timeout=REQUEST_TIMEOUT_SEC)
            res.raise_for_status()
        except requests.RequestException as e:
            raise MolitApiError(f"국토교통부 API 호출 실패 (LAWD_CD={lawd_cd}, {deal_ymd}): {e}") from e

        items.extend(_parse_response(res.text, lawd_cd, deal_ymd))

    return items


def _normalize_building(item: dict, region_name: str) -> dict | None:
    """실거래 item 하나를 calculator.py가 쓰는 '건물(매물)' 카드 형태로 정규화한다.
    필수 필드(아파트명/보증금/월세)가 없으면 None."""
    apt_name = _pick(item, "apt_name")
    deposit = _parse_amount(_pick(item, "deposit"))
    monthly_rent = _parse_amount(_pick(item, "monthly_rent"))
    if not apt_name or deposit is None or monthly_rent is None:
        return None

    area = _parse_amount(item.get("excluUseAr"))
    deal_year = item.get("dealYear", "")
    deal_month = item.get("dealMonth", "")

    return {
        "region": region_name,
        "building_name": apt_name,
        "dong": item.get("umdNm", ""),
        "deposit": round(deposit),
        "monthly_rent": round(monthly_rent),
        "exclusive_area": area,
        "floor": item.get("floor", ""),
        "deal_date": f"{deal_year}.{deal_month}" if deal_year and deal_month else "",
    }


@lru_cache(maxsize=64)
def fetch_candidate_buildings(lawd_cds: tuple[str, ...], region_name: str) -> list[dict]:
    """지역(법정동코드 튜플)의 최근 실거래 내역을 개별 '건물(매물)' 후보 리스트로 반환한다.
    calculator.py가 이 중에서 예산에 맞는 후보를 골라 추천한다.

    전세(월세 0원) 거래는 이 앱이 월세 기준 계산을 하므로 제외한다.
    """
    items = []
    for lawd_cd in lawd_cds:
        try:
            items.extend(fetch_recent_rent_items(lawd_cd))
        except MolitApiError as e:
            logger.warning(str(e))

    buildings = []
    for item in items:
        building = _normalize_building(item, region_name)
        if building and building["monthly_rent"] > 0:
            buildings.append(building)

    return buildings
