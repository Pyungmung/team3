"""
[담당: 송귀성] customhouse-ai (FastAPI) 엔트리포인트

실행:
    uvicorn main:app --reload --port 8000
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import diagnosis, market_price, policy
from app.core.config import settings

app = FastAPI(
    title="CustomHouse AI Engine",
    description="맞집 - AI 주거비 절약 추천 엔진 (실질 주거비 계산 / 지역 매칭 / 정책 추천)",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(diagnosis.router, prefix="/api/v1")
app.include_router(policy.router, prefix="/api/v1")
app.include_router(market_price.router, prefix="/api/v1")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": settings.app_name}
