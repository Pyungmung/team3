"""
[담당: 송귀성] 매매/전세 단지 시세(참고용) 응답 스키마
"""
from pydantic import BaseModel


class MarketPriceItem(BaseModel):
    complex_name: str
    min_price: int          # 만원
    max_price: int          # 만원
    median_price: int | None = None  # 만원


class MarketPriceResponse(BaseModel):
    region: str
    sale: list[MarketPriceItem]   # 매매 시세 (단지 단위, 최대 5개)
    lease: list[MarketPriceItem]  # 전세 시세 (단지 단위, 최대 5개)
    disclaimer: str = (
        "네이버 부동산 비공식 API 기반 참고 정보이며, 실시간 시세와 다를 수 있습니다. "
        "개별 매물이 아닌 단지 단위 가격 범위입니다."
    )
