"""
[담당: 송귀성] 메인 홈 "AI 주거 진단 미리보기" 전용 가벼운 요약 API (2026-10-06)
POST /api/v1/diagnosis/home-preview - 전체 진단과 같은 계산을 쓰되 상위 몇 건만 뽑고 요약 값 몇 개만 돌려준다.
"""
from fastapi import APIRouter, HTTPException

from app.models.request_schema import DiagnosisRequest
from app.services import listing_recommender

router = APIRouter(prefix="/diagnosis/home-preview", tags=["home-preview"])


@router.post("")
def home_preview(request: DiagnosisRequest) -> dict:
    try:
        return listing_recommender.run_home_preview(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
