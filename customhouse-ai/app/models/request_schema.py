"""
[담당: 송귀성] Pydantic 요청 스키마
백엔드(Spring Boot)로부터 전달받는 [소득, 보증금, 희망 월세, 직장위치] 데이터 검증.

주의: 백엔드의 RecommendRequest는 Java record라 선택 입력 필드를 비워도
JSON에 "필드": null 형태로 명시적으로 전송된다. maxCommuteMinutes처럼
기본값이 있는 필드도 null이 들어올 수 있으므로, before-validator로
None → 기본값 치환을 해준다 (단순히 Field(default=...)만으로는
명시적 null을 막지 못한다).

2026-09-16: "월급(월 단위)" 입력을 없애고 "연소득"으로 통합했다 (마이페이지 프로필 확장과
함께 진행). 예산/RIR 계산과 정책 소득 기준 판별 모두 effective_monthly_income
(= max(연소득, 부부합산 연소득) / 12) 하나로 통일해서 쓴다 - 부부합산 소득이 있으면 그중
더 큰 쪽을 실제 심사 기준으로 보는 게 실제 청년 정책(버팀목대출 등)의 통상적인 방식이다.
"""
from pydantic import BaseModel, Field, field_validator


class DiagnosisRequest(BaseModel):
    annual_income: int = Field(..., ge=0, description="소득 (연소득, 만원)", alias="annualIncome")
    couple_annual_income: int | None = Field(
        None, ge=0, description="부부합산 연소득 (만원, 선택). 있으면 연소득과 비교해 더 큰 값을 "
        "정책/예산 계산에 쓴다.", alias="coupleAnnualIncome",
    )
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
    job_type: str | None = Field(
        None, description="직업종류 (GOVERNMENT/SME/MID_SIZED/LARGE_CORP, 선택). 정책의 "
        "eligible_job_types 조건 판별용.", alias="jobType",
    )
    preferential_statuses: list[str] = Field(
        default_factory=list,
        description="우대사항 (BASIC_LIVELIHOOD/NEAR_POVERTY/SINGLE_PARENT/INDEPENDENT_YOUTH/"
        "NEWLYWED/MULTI_CHILD 중 다중 선택, 선택). 정책의 required_preferential_status 조건 판별용.",
        alias="preferentialStatuses",
    )

    move_schedule: str | None = Field(
        None,
        description="희망 이사 일정 (IMMEDIATE/WITHIN_3M/WITHIN_6M/EXPLORING). 아직 추천 로직에는 쓰지 않고 "
        "나중에 데이터로 활용하기 위해 받아만 둔다.",
        alias="moveSchedule",
    )
    transport_type: str | None = Field(
        None,
        description="주요 통근 수단 (PUBLIC/WALK/CAR). 아직 추천 로직에는 쓰지 않고 나중에 데이터로 활용하기 위해 받아만 둔다.",
        alias="transportType",
    )
    use_loan_policy: bool = Field(
        True,
        description="정책 대출 활용 의향. false면 정부지원정책 목록에서 대출 상품(policies.json의 is_loan)을 추천하지 않는다.",
        alias="useLoanPolicy",
    )
    preferred_building_types: list[str] = Field(
        default_factory=list,
        description="선호 주택 유형(국토부 실거래가 API 유형명 그대로: 아파트/오피스텔/연립다세대/단독다가구). "
        "비어 있거나 4종 모두면 필터 없이 전체를, 일부만 있으면 그 유형의 매물만 추천한다.",
        alias="preferredBuildingTypes",
    )

    class Config:
        populate_by_name = True

    @field_validator("max_commute_minutes", mode="before")
    @classmethod
    def _default_max_commute(cls, v):
        return 40 if v is None else v

    @field_validator("preferential_statuses", "preferred_building_types", mode="before")
    @classmethod
    def _default_empty_list(cls, v):
        return [] if v is None else v

    @field_validator("use_loan_policy", mode="before")
    @classmethod
    def _default_use_loan_policy(cls, v):
        return True if v is None else v

    @property
    def effective_monthly_income(self) -> float:
        """예산(RIR)·정책 소득기준 계산에 공용으로 쓰는 실효 월소득.
        부부합산 연소득이 있으면 본인 연소득과 비교해 더 큰 쪽을 12로 나눈다."""
        annual = max(self.annual_income, self.couple_annual_income or 0)
        return annual / 12
