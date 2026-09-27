"""
[담당: 송귀성] Pydantic 응답 스키마
진단 결과 및 추천 리포트 규격을 정의한다.
"""
from pydantic import BaseModel


class MatchedPolicy(BaseModel):
    """docs/housing_policy_list.csv 기반 실제 정책 매칭 결과. "주거정책 추천" 표에
    보여주는 정보성 데이터이며, 지원혜택이 자유 텍스트라 real_housing_cost 계산에는
    반영하지 않는다 (calculator.py/policy_matcher.py 참고)."""

    id: str
    name: str
    agency: str  # 소관 기관명 (예: 국토교통부, 서울특별시, 주택도시기금)
    description: str  # 지원혜택 내용 (자유 텍스트)


class BuildingRecommendation(BaseModel):
    """지역 평균이 아니라 실제 국토부 실거래 내역 1건(개별 아파트 매물) 단위 추천."""

    region: str             # 구/시 (통근시간·정책·지도 마커 기준)
    property_type: str = ""  # 아파트/오피스텔/연립다세대/단독다가구 (샘플 폴백 매물은 빈 문자열)
    building_name: str      # 건물명 (건물명 없는 단독다가구는 "역삼동 다가구", 샘플이면 "OO구 평균 시세 (샘플)")
    dong: str = ""          # 법정동명 (예: 개포동)
    address: str = ""       # 표시용 상세주소. 도로명(아파트는 국토부 API 자체 제공, 오피스텔·연립다세대는
    # juso_api.py 변환) 우선, 없으면 지번주소, 단독다가구처럼 지번조차 없으면 빈 문자열(프론트가 region+dong으로 대체)
    lat: float | None = None  # 매물 실제 위도 (카카오 로컬 API로 address를 지오코딩, kakao_geocode.py)
    lon: float | None = None  # 매물 실제 경도. address가 없거나 지오코딩 실패 시 None - 프론트가 지역 대표좌표로 대체
    exclusive_area: float | None = None  # 전용면적(㎡)
    floor: str = ""
    deal_date: str = ""     # 실거래 기준월 "2024.8" (샘플이면 빈 문자열)
    lease_type: str = "월세"  # "전세" | "월세"
    is_semi_jeonse: bool = False  # 월세인데 보증금이 커서(반전세 성격) 별도 배지로 구분할 매물
    commute_minutes: int
    commute_source: str  # "카카오 길찾기 API(자동차)" / "카카오맵 API(대중교통)" / "카카오맵 API(도보)" - 보통 실제 API 결과.
    # API가 완전히 막혔을 때만 calculator.py의 ESTIMATE_SOURCE_LABEL("직선거리 추정(비상용)")이 온다.
    listing_deposit: int      # 만원, 그 매물의 실제 보증금 (실거래가 원본 - 가장 중요한 표시값)
    listing_monthly_rent: int  # 만원, 그 매물의 실제 월세 (실거래가 원본 - 가장 중요한 표시값)
    # 2026-09-28: 월세는 "보증금을 조정해서 월세를 조정"하는 계산(전월세전환율/대출금리 가정)을
    # 없앴다 - 정확한 금리를 알 수 없는 불확실한 가정으로 실거래 원본을 왜곡하지 않기 위함
    # (요청 반영). 그래서 월세 매물은 rent == listing_monthly_rent, loan_interest == 0 이다.
    # 전세는 보증금이 곧 비용의 핵심이라 비교할 다른 실측 기준이 없어, 보증금 차액을 대출이자로
    # 환산해 비교하는 기존 방식을 그대로 유지한다.
    rent: int              # 만원. 월세는 항상 listing_monthly_rent와 같음. 전세는 사용자 보증금 반영 후 월세(보통 0)
    maintenance_fee: int   # 만원
    loan_interest: int     # 만원 (월 환산). 월세는 항상 0, 전세만 보증금 차액에 대한 대출이자 반영
    transportation_cost: int  # 만원
    government_support: int   # 만원, 항상 0 (정책이 자유 텍스트라 실질 주거비 계산엔 미반영 - matched_policies 참고)
    real_housing_cost: int    # 만원 = rent + maintenance_fee + loan_interest + transportation_cost - government_support
    baseline_cost: int        # 만원, 비교 기준. 월세는 항상 real_housing_cost와 같음(비교 기준 없음), 전세만 유의미
    monthly_savings: int      # 만원, baseline_cost - real_housing_cost. 월세는 항상 0
    matched_policies: list[MatchedPolicy]
    data_source: str = "샘플 데이터"  # "국토부 실거래가 (2024.8 거래)" 또는 "샘플 데이터"


class DiagnosisResponse(BaseModel):
    affordable_rent: int          # 만원, 소득의 30% 기준 적정 월세 상한
    rent_to_income_ratio: float   # RIR(%) 참고용
    wolse_recommendations: list[BuildingRecommendation]   # 월세 매물 추천 (실거래 월세 기준 저렴한 순, 보증금 조정 계산 없음)
    jeonse_recommendations: list[BuildingRecommendation]  # 전세 매물 추천 (월세=0, 실질 주거비는 대출이자 중심)
    used_distance_estimate: bool = False  # 통근시간 중 하나라도 비상용 직선거리 추정을 썼으면 True
    # (카카오 API가 완전히 막혔을 때만 - calculator.py의 ESTIMATE_SOURCE_LABEL 참고). 프론트가
    # 화면 한구석에 "API 실측 기반"/"일부 직선거리 추정 사용됨" 표시하는 데 쓴다.
    disclaimer: str = "본 결과는 샘플 데이터 기반 추정치이며, 실제 시세·정책 자격과 다를 수 있습니다."
