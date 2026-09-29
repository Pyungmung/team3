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


class LoanPreferenceSetting(BaseModel):
    """대출 1개의 우대사항 1개 설정 (관리자 화면). required=True면 그 우대사항에 해당해야 대출 자격이 되고,
    discount는 해당할 때 깎아주는 우대금리(%p) - 자격 판별에는 쓰지 않고 이자 계산 단계에서 쓴다.
    2026-09-30: override_* 5종은 신혼부부/다자녀가구처럼 일부 우대사항이 공통 조건과 다른 한도를 쓸 때의
    재반영 값이다 - None이면 공통 조건(max_listing_deposit 등)을 그대로 쓰고, 값이 있고 사용자가 그
    우대사항에 해당하면 그 값으로 최종 한도가 바뀐다 (loan_matcher._effective_limit 참고)."""

    required: bool = False
    discount: float = 0.0
    override_max_listing_deposit: int | None = Field(None, alias="overrideMaxListingDeposit")
    override_max_income_single: int | None = Field(None, alias="overrideMaxIncomeSingle")
    override_max_income_couple: int | None = Field(None, alias="overrideMaxIncomeCouple")
    override_max_loan_amount: int | None = Field(None, alias="overrideMaxLoanAmount")
    override_max_loan_ratio_percent: float | None = Field(None, alias="overrideMaxLoanRatioPercent")

    class Config:
        populate_by_name = True


class IncomeStandardCondition(BaseModel):
    """관리자 화면(관리자 수정 > 기준소득관리)에서 저장한 기준소득 통계. 백엔드가 DB에서 읽어 요청에 실어 보낸다
    (브라우저가 보낸 값이 아니다). 값이 없는 필드는 None - 그 경우 호출하는 쪽(rir_stats/policy_matcher)이
    기존 CSV/JSON 폴백으로 대신한다 (2026-09-30: docs/RIR.csv + docs/housing_policy_list.csv 15열을 대체).
    RIR은 % 단위, 기준중위소득은 원 단위(1원까지 정확한 정수, 보건복지부 고시 원문 그대로)."""

    rir_overall_percent: float | None = Field(None, alias="rirOverallPercent")   # 전국(전체) - 리포트 참고용
    rir_metro_percent: float | None = Field(None, alias="rirMetroPercent")       # 수도권 - 리포트 최상위 기준값
    rir_low_percent: float | None = Field(None, alias="rirLowPercent")          # 하위(1-4분위)
    rir_mid_percent: float | None = Field(None, alias="rirMidPercent")          # 중위(5-8분위)
    rir_high_percent: float | None = Field(None, alias="rirHighPercent")        # 상위(9-10분위)
    rir_year: int | None = Field(None, alias="rirYear")
    rir_source: str | None = Field(None, alias="rirSource")
    median_income_100_percent_monthly_won: int | None = Field(None, alias="medianIncome100PercentMonthly")

    class Config:
        populate_by_name = True


class LoanProductCondition(BaseModel):
    """관리자 화면(관리자 수정 > 전세자금대출)에서 저장한 대출 1종의 자격 조건. 백엔드가 DB에서 읽어 요청에 실어 보낸다
    (브라우저가 보낸 값이 아니다). 값이 None인 조건은 "제한 없음". 금액은 만원, 면적은 ㎡."""

    type: str                       # 대출 코드 (GENERAL_BEOTIMMOK 등)
    name: str                       # 화면에 보여줄 대출 이름
    lease_type: str = Field("전세", alias="leaseType")   # 이 대출이 붙는 매물 유형: 전세 / 월세
    min_age: int | None = Field(None, alias="minAge")
    max_age: int | None = Field(None, alias="maxAge")
    max_income_single: int | None = Field(None, alias="maxIncomeSingle")
    max_income_couple: int | None = Field(None, alias="maxIncomeCouple")
    max_asset: int | None = Field(None, alias="maxAsset")
    max_listing_deposit: int | None = Field(None, alias="maxListingDeposit")
    max_exclusive_area: float | None = Field(None, alias="maxExclusiveArea")
    max_loan_ratio_percent: float | None = Field(None, alias="maxLoanRatioPercent")  # 매물 보증금의 이 비율(%)까지만 대출 가능
    max_loan_amount: int | None = Field(None, alias="maxLoanAmount")  # 만원, 매물과 무관한 대출 절대 상한
    preferences: dict[str, LoanPreferenceSetting] = Field(default_factory=dict)
    # 대출금리표: [행(부부합산 연소득 4구간)][열(임차보증금 3구간)] = 연 금리(%). None이면 아직 실제 금리표가 없어
    # loan_matcher.DEFAULT_BASE_RATE_PERCENT(임시 고정금리)를 쓴다 - loan_matcher.RATE_TABLE_INCOME_BRACKETS_MANWON/
    # RATE_TABLE_DEPOSIT_BRACKETS_MANWON이 행/열의 구간 정의다.
    rate_table: list[list[float]] | None = Field(None, alias="rateTable")

    class Config:
        populate_by_name = True

    @field_validator("preferences", mode="before")
    @classmethod
    def _default_preferences(cls, v):
        return {} if v is None else v


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
    max_commute_minutes: int = Field(30, ge=10, description="희망 최대 통근시간(분)", alias="maxCommuteMinutes")
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
        description="주요 통근 수단 (PUBLIC/WALK/CAR, 미지정 시 PUBLIC). 각 수단에 맞는 카카오 API로 "
        "실제 소요시간을 계산한다 (CAR=카카오모빌리티 자동차 길찾기, PUBLIC/WALK=카카오맵 대중교통/도보 "
        "경로 조회. calculator.py의 _commute_minutes 참고). API 호출 실패/결과 없음이면 직선거리로 "
        "추정하지 않고 그 지역을 후보에서 제외한다.",
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

    # 관리자 화면에서 저장한 대출 조건. 백엔드(Spring)가 DB에서 읽어 실어 보내며 브라우저 입력이 아니다.
    # 비어 있으면(저장된 대출 없음/조회 실패) 대출 자격 판별은 하지 않는다.
    loan_products: list[LoanProductCondition] = Field(default_factory=list, alias="loanProducts")

    # 관리자 화면(관리자 수정 > 기준소득관리)에서 저장한 RIR/기준중위소득. 백엔드가 DB에서 읽어 실어 보내며
    # 브라우저 입력이 아니다. None이면(조회 실패 등) CSV/JSON 폴백을 쓴다 (IncomeStandardCondition 참고).
    income_standard: IncomeStandardCondition | None = Field(None, alias="incomeStandard")

    class Config:
        populate_by_name = True

    @field_validator("max_commute_minutes", mode="before")
    @classmethod
    def _default_max_commute(cls, v):
        return 30 if v is None else v

    @field_validator("preferential_statuses", "preferred_building_types", "loan_products", mode="before")
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
