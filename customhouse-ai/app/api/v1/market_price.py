"""
[담당: 송귀성] 매매/전세 단지 시세(참고용) API
diagnosis(월세 실질 주거비 계산)와는 별개의 보조 엔드포인트. 프론트엔드가 추천 지역 옆에
"이 동네 매매/전세 시세는 대략 이 정도" 참고 정보를 보여줄 때 선택적으로 호출한다.
"""
from fastapi import APIRouter, HTTPException

from app.models.market_price_schema import MarketPriceResponse
from app.services import naver_land_collector

router = APIRouter(prefix="/market-price", tags=["market-price"])


@router.get("", response_model=MarketPriceResponse)
def get_market_price(region: str) -> MarketPriceResponse:
    coords = naver_land_collector.get_region_coords(region)
    if not coords:
        raise HTTPException(status_code=404, detail=f"좌표 정보가 없는 지역입니다: {region}")

    lat, lon = coords
    sale = naver_land_collector.summarize_market_price(lat, lon, naver_land_collector.TRADE_SALE)
    lease = naver_land_collector.summarize_market_price(lat, lon, naver_land_collector.TRADE_LEASE)

    return MarketPriceResponse(region=region, sale=sale, lease=lease)
