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


class BuildingRecommendation(BaseModel):
    """지역 평균이 아니라 실제 국토부 실거래 내역 1건(개별 아파트 매물) 단위 추천."""

    region: str             # 구/시 (통근시간·정책·지도 마커 기준)
    building_name: str      # 아파트명 (실거래 데이터 없으면 "OO구 평균 시세 (샘플)")
    dong: str = ""          # 법정동명 (예: 개포동)
    exclusive_area: float | None = None  # 전용면적(㎡)
    floor: str = ""
    deal_date: str = ""     # 실거래 기준월 "2024.8" (샘플이면 빈 문자열)
    lease_type: str = "월세"  # "전세" | "월세"
    is_semi_jeonse: bool = False  # 월세인데 보증금이 커서(반전세 성격) 별도 배지로 구분할 매물
    commute_minutes: int
    listing_deposit: int      # 만원, 그 매물의 실제 보증금 (실거래가 원본)
    listing_monthly_rent: int  # 만원, 그 매물의 실제 월세 (실거래가 원본, 보증금 전환 적용 전)
    rent: int              # 만원, 사용자 보증금으로 전환/대출 계산까지 적용한 후의 월세
    maintenance_fee: int   # 만원
    loan_interest: int     # 만원 (월 환산)
    transportation_cost: int  # 만원
    government_support: int   # 만원 (월 환산, 정책 지원금 합계)
    real_housing_cost: int    # 만원 = rent + maintenance_fee + loan_interest + transportation_cost - government_support
    baseline_cost: int        # 만원, 정책/보증금 최적화 미적용 시 비교 기준
    monthly_savings: int      # 만원, baseline_cost - real_housing_cost
    matched_policies: list[MatchedPolicy]
    data_source: str = "샘플 데이터"  # "국토부 실거래가 (2024.8 거래)" 또는 "샘플 데이터"


class DiagnosisResponse(BaseModel):
    affordable_rent: int          # 만원, 소득의 30% 기준 적정 월세 상한
    rent_to_income_ratio: float   # RIR(%) 참고용
    wolse_recommendations: list[BuildingRecommendation]   # 월세 매물 추천 (실질 주거비 = 월세+관리비+대출이자+교통비-지원금)
    jeonse_recommendations: list[BuildingRecommendation]  # 전세 매물 추천 (월세=0, 실질 주거비는 대출이자 중심)
    disclaimer: str = "본 결과는 샘플 데이터 기반 추정치이며, 실제 시세·정책 자격과 다를 수 있습니다."
