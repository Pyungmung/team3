"""
[담당: 송귀성] customhouse-ai (FastAPI) 엔트리포인트

실행:
    uvicorn main:app --reload --port 8000
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import home_preview, listing_commute, listing_reference, listing_registration, listings, market_price, policy
from app.core.config import settings
from app.services import listing_sync


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 서버가 켜질 때 백엔드 DB의 회원 등록 매물을 CSV에 되살린다 (재배포·유휴 재시작으로 사라진 매물 복원, 백그라운드)
    listing_sync.start_background_sync()
    yield


app = FastAPI(
    title="CustomHouse AI Engine",
    description="맞집 - AI 주거비 절약 추천 엔진 (실질 주거비 계산 / 지역 매칭 / 정책 추천)",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(home_preview.router, prefix="/api/v1")  # /diagnosis/home-preview - listings의 /diagnosis/listings/{id}와 겹치지 않는 경로
app.include_router(listings.router, prefix="/api/v1")
app.include_router(listing_reference.router, prefix="/api/v1")
app.include_router(listing_commute.router, prefix="/api/v1")
app.include_router(listing_registration.router, prefix="/api/v1")
app.include_router(policy.router, prefix="/api/v1")
app.include_router(market_price.router, prefix="/api/v1")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": settings.app_name}
