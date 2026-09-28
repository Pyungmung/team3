"""
[담당: 송귀성] 더미 매물 추천 API
POST /api/v1/diagnosis/listings - 기존 POST /api/v1/diagnosis(국토부 실거래가 기준)와 같은 요청 본문을 받아
docs/samples/dummyhouses CSV의 더미 매물을 추천한다. 실거래가는 매물별 참고 정보로만 내려간다.
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
