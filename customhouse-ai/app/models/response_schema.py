"""
[담당: 송귀성] Pydantic 응답 스키마
진단 결과 및 추천 리포트 규격을 정의한다.
"""
from pydantic import BaseModel


class MatchedPolicy(BaseModel):
    id: str
    name: str
    type: str  # SUBSIDY | LOAN
    description: str
    monthly_benefit: int = 0  # 만원, SUBSIDY인 경우
    loan_rate_annual_percent: float | None = None  # LOAN인 경우


class RegionRecommendation(BaseModel):
    region: str
    commute_minutes: int
    rent: int              # 만원
    maintenance_fee: int   # 만원
    loan_interest: int     # 만원 (월 환산)
    transportation_cost: int  # 만원
    government_support: int   # 만원 (월 환산, 정책 지원금 합계)
    real_housing_cost: int    # 만원 = rent + maintenance_fee + loan_interest + transportation_cost - government_support
    baseline_cost: int        # 만원, 정책/보증금 최적화 미적용 시 비교 기준
    monthly_savings: int      # 만원, baseline_cost - real_housing_cost
    matched_policies: list[MatchedPolicy]
    data_source: str = "샘플 데이터"  # "국토부 실거래가 (최근 N건 평균)" 또는 "샘플 데이터"


class DiagnosisResponse(BaseModel):
    affordable_rent: int          # 만원, 소득의 30% 기준 적정 월세 상한
    rent_to_income_ratio: float   # RIR(%) 참고용
    recommendations: list[RegionRecommendation]
    disclaimer: str = "본 결과는 샘플 데이터 기반 추정치이며, 실제 시세·정책 자격과 다를 수 있습니다."
