"""
[담당: 송귀성] Pydantic 요청 스키마
백엔드(Spring Boot)로부터 전달받는 [월급, 보증금, 희망 월세, 직장위치] 데이터 검증.

주의: 백엔드의 RecommendRequest는 Java record라 선택 입력 필드를 비워도
JSON에 "필드": null 형태로 명시적으로 전송된다. maxCommuteMinutes처럼
기본값이 있는 필드도 null이 들어올 수 있으므로, before-validator로
None → 기본값 치환을 해준다 (단순히 Field(default=...)만으로는
명시적 null을 막지 못한다).
"""
from pydantic import BaseModel, Field, field_validator


class DiagnosisRequest(BaseModel):
    monthly_income: int = Field(..., ge=0, description="월급 (만원)", alias="monthlyIncome")
    deposit: int = Field(..., ge=0, description="보유 보증금 (만원)", alias="deposit")
    desired_rent: int | None = Field(None, ge=0, description="희망 월세 (만원, 선택)", alias="desiredRent")
    work_location: str = Field(..., min_length=1, description="직장 위치 (예: 강남구)", alias="workLocation")
    max_commute_minutes: int = Field(40, ge=10, description="희망 최대 통근시간(분)", alias="maxCommuteMinutes")
    age: int | None = Field(None, ge=0, le=120, description="나이 (정책 자격 판별용)", alias="age")
    no_householder: bool | None = Field(None, description="무주택 세대주 여부 (버팀목 대출 자격 판별용)", alias="noHouseholder")

    class Config:
        populate_by_name = True

    @field_validator("max_commute_minutes", mode="before")
    @classmethod
    def _default_max_commute(cls, v):
        return 40 if v is None else v
