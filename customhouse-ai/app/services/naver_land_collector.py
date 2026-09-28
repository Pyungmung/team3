"""
[담당: 송귀성] 네이버 부동산 매매/전세 단지 시세 - 참고용 보조 기능

⚠️ 네이버 부동산은 공식 Open API를 제공하지 않는다. 이 모듈은 브라우저 개발자도구로 확인
가능한 내부 API(new.land.naver.com)를 호출하며, 이는 네이버 이용약관상 비인가 크롤링에
해당할 수 있어 법적/차단(IP 밴, 요청 형식 변경 등) 리스크가 있다는 점을 팀에서 인지하고
"참고용 보조 기능"으로만 쓰기로 확인했다 (2026-09-15).

핵심 제약: 이 API(complexes/single-markers/2.0)는 "단지" 단위 매매(A1)/전세(B1) 가격
범위만 제공하고, 월세(B2)는 동작하지 않는 것으로 확인되어 있다. 그래서 맞집의 핵심 기능인
월세 기반 실질 주거비 계산에는 쓸 수 없고, 국토교통부 실거래가(api_collector.py)가 계속
그 역할을 한다 — 여기서는 "이 동네 매매/전세 시세는 대략 이 정도"라는 참고 정보만 제공한다.
개별 매물(방 사진이 있는 실제 등록 매물)이 아니라 단지 전체의 가격 범위임에 유의.
"""
import json
import logging
from functools import lru_cache

import requests

from app.core.config import settings

logger = logging.getLogger(__name__)

NAVER_LAND_BASE_URL = "https://new.land.naver.com/api/"
REQUEST_TIMEOUT_SEC = 5
REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    "Referer": "https://new.land.naver.com/",
    "Accept": "application/json",
}

TRADE_SALE = "A1"   # 매매
TRADE_LEASE = "B1"  # 전세
ESTATE_APT = "APT"

# 좌표 중심으로 주변 범위를 얼마나 잡을지 (원본 참고값, 대략 동네 하나 폭)
_LAT_SPAN = 0.0069786
_LON_SPAN = 0.0137329


class NaverLandApiError(Exception):
    """네이버 부동산 비공식 API 호출 실패 (호출부에서 잡아서 빈 결과로 폴백시킨다)."""


def _get(path: str, params: dict):
    try:
        res = requests.get(
            NAVER_LAND_BASE_URL + path, params=params, headers=REQUEST_HEADERS, timeout=REQUEST_TIMEOUT_SEC
        )
        res.raise_for_status()
    except requests.RequestException as e:
        raise NaverLandApiError(f"네이버 부동산 API 호출 실패: {e}") from e

    try:
        return res.json()
    except ValueError as e:
        raise NaverLandApiError(f"네이버 부동산 API 응답이 JSON이 아닙니다: {res.text[:200]}") from e


def _find_cortar_no(lat: float, lon: float) -> str | None:
    """좌표로 네이버 부동산의 내부 지역코드(cortarNo)를 찾는다."""
    data = _get("cortars", {"centerLat": lat, "centerLon": lon, "zoom": 16})
    if not isinstance(data, dict):
        return None
    return data.get("cortarNo")


def _complex_marker_params(lat: float, lon: float, cortar_no: str, trade_type: str) -> dict:
    return {
        "cortarNo": cortar_no,
        "zoom": 16,
        "priceType": "RETAIL",
        "markerId": "",
        "markerType": "",
        "selectedComplexNo": "",
        "selectedComplexBuildingNo": "",
        "fakeComplexMarker": "",
        "tag": "::::::::",
        "rentPriceMin": 0,
        "rentPriceMax": 900000000,
        "priceMin": 0,
        "priceMax": 900000000,
        "areaMin": 0,
        "areaMax": 900000000,
        "showArticle": True,
        "sameAddressGroup": False,
        "tradeType": trade_type,
        "realEstateType": ESTATE_APT,
        "leftLon": lon - _LON_SPAN,
        "rightLon": lon + _LON_SPAN,
        "topLat": lat + _LAT_SPAN,
        "bottomLat": lat - _LAT_SPAN,
    }


def summarize_market_price(lat: float, lon: float, trade_type: str, max_complexes: int = 5) -> list[dict]:
    """좌표 주변 아파트 단지들의 매매/전세 가격 범위(단지 단위, 참고용)를 가격 낮은 순 상위 N개로 반환한다.
    API 호출이 실패하거나 결과가 없으면 조용히 빈 리스트를 반환한다 (호출부가 있어도 그만, 없어도 그만인
    보조 정보로 취급하도록)."""
    try:
        cortar_no = _find_cortar_no(lat, lon)
        if not cortar_no:
            return []
        data = _get("complexes/single-markers/2.0", _complex_marker_params(lat, lon, cortar_no, trade_type))
    except NaverLandApiError as e:
        logger.warning(str(e))
        return []

    if not isinstance(data, list):
        return []

    complexes = []
    for item in data:
        if trade_type == TRADE_SALE:
            count = item.get("dealCount", 0)
            min_price, max_price, median_price = item.get("minDealPrice"), item.get("maxDealPrice"), item.get("medianDealPrice")
        else:
            count = item.get("leaseCount", 0)
            min_price, max_price, median_price = item.get("minLeasePrice"), item.get("maxLeasePrice"), item.get("medianLeasePrice")

        if not count or not min_price:
            continue

        complexes.append(
            {
                "complex_name": item.get("complexName", ""),
                "min_price": min_price,      # 만원
                "max_price": max_price,      # 만원
                "median_price": median_price,  # 만원
            }
        )

    complexes.sort(key=lambda c: c["min_price"])
    return complexes[:max_complexes]


@lru_cache(maxsize=1)
def _region_coords() -> dict[str, list[float]]:
    with open(settings.region_coords_file, encoding="utf-8") as f:
        return json.load(f)["regions"]


def get_region_coords(region_name: str) -> tuple[float, float] | None:
    """지역명(예: 강남구) -> (lat, lon). region_coords.json에 없는 지역이면 None."""
    coords = _region_coords().get(region_name)
    return (coords[0], coords[1]) if coords else None
