"""
[담당: 송귀성] 더미 매물 추천 API
POST /api/v1/diagnosis/listings - docs/samples/dummyhouses CSV의 더미 매물을 추천한다.
"""
from fastapi import APIRouter, HTTPException

from app.models.request_schema import DiagnosisRequest
from app.models.response_schema import ListingDiagnosisResponse, ListingRefreshResponse
from app.services import listing_recommender

router = APIRouter(prefix="/diagnosis/listings", tags=["diagnosis-listings"])


@router.post("", response_model=ListingDiagnosisResponse)
def diagnose_listings(request: DiagnosisRequest) -> ListingDiagnosisResponse:
    try:
        result = listing_recommender.run_listing_diagnosis(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    return ListingDiagnosisResponse(**result)


@router.post("/{listing_id}", response_model=ListingRefreshResponse)
def refresh_listing(listing_id: str, request: DiagnosisRequest) -> ListingRefreshResponse:
    """관심매물 새로고침 - 매물번호 1건을 사용자의 현재 조건으로 다시 계산한 카드를 돌려준다."""
    try:
        result = listing_recommender.refresh_listing_card(request, listing_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    if result is None:
        raise HTTPException(status_code=404, detail="매물을 찾을 수 없어요.")
    return ListingRefreshResponse(**result)
