"""
[담당: 송귀성] 실질 주거비 계산 & 최적 지역 매칭 API
백엔드(Spring Boot, domain/recommendation)가 호출하는 핵심 엔드포인트.
"""
from fastapi import APIRouter, HTTPException

from app.models.request_schema import DiagnosisRequest
from app.models.response_schema import DiagnosisResponse
from app.services import calculator

router = APIRouter(prefix="/diagnosis", tags=["diagnosis"])


@router.post("", response_model=DiagnosisResponse)
def diagnose(request: DiagnosisRequest) -> DiagnosisResponse:
    try:
        result = calculator.run_diagnosis(request)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    return DiagnosisResponse(**result)
