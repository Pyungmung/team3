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
    deposit: int = Field(..., ge=0, description="현재 보유 보증금 (만원, 지금 수중에 있는 현금)", alias="deposit")
    desired_deposit: int | None = Field(
        None,
        ge=0,
        description="희망 보증금/전세액 (만원, 선택). 입력하면 그 금액 이하(실거래 보증금 <= 입력값)인 "
        "매물만 검색 대상으로 삼고, 보증금 전환/대출 계산에서도 deposit 대신 이 값을 쓴다.",
        alias="desiredDeposit",
    )
    desired_rent: int | None = Field(
        None,
        ge=0,
        description="희망 월세 (만원, 선택). 입력하면 그 금액 이하(실거래 월세 <= 입력값)인 매물만 "
        "검색 대상으로 삼고, 비교 기준(baseline) 월세로도 쓴다.",
        alias="desiredRent",
    )
    work_location: str = Field(..., min_length=1, description="직장 위치 (예: 강남구)", alias="workLocation")
    work_lat: float | None = Field(
        None, description="직장 정확한 위도 (선택). 카카오 주소검색으로 얻은 좌표. 있으면 26개 구 "
        "단위 대표좌표 대신 이 좌표로 통근시간을 계산한다 (work_lon과 함께 와야 함).",
        alias="workLat",
    )
    work_lon: float | None = Field(None, description="직장 정확한 경도 (선택, work_lat 참고)", alias="workLon")
    max_commute_minutes: int = Field(40, ge=10, description="희망 최대 통근시간(분)", alias="maxCommuteMinutes")
    age: int | None = Field(None, ge=0, le=120, description="나이 (정책 자격 판별용)", alias="age")
    no_householder: bool | None = Field(None, description="무주택 세대주 여부 (버팀목 대출 자격 판별용)", alias="noHouseholder")
    assets: int | None = Field(None, ge=0, description="총자산 (만원, 정책 자격의 자산 기준 판별용)", alias="assets")

    class Config:
        populate_by_name = True

    @field_validator("max_commute_minutes", mode="before")
    @classmethod
    def _default_max_commute(cls, v):
        return 40 if v is None else v
