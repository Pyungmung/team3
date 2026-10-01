"""
[담당: 송귀성] 더미 매물 추천 API
POST /api/v1/diagnosis/listings - docs/samples/dummyhouses CSV의 더미 매물을 추천한다.
"""
from fastapi import APIRouter, HTTPException

from app.models.request_schema import DiagnosisRequest
from app.models.response_schema import ListingDiagnosisResponse
from app.services import listing_recommender

router = APIRouter(prefix="/diagnosis/listings", tags=["diagnosis-listings"])


@router.post("", response_model=ListingDiagnosisResponse)
def diagnose_listings(request: DiagnosisRequest) -> ListingDiagnosisResponse:
    try:
        result = listing_recommender.run_listing_diagnosis(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    return ListingDiagnosisResponse(**result)
