"""
[담당: 송귀성] Pydantic 응답 스키마
진단 결과 및 추천 리포트 규격을 정의한다.
"""
from pydantic import BaseModel, Field


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
    baseline_cost: int        # 만원, 비교 기준. 월세/전세 모두 real_housing_cost와 같음(비교할 별도 기준 없음)
    monthly_savings: int      # 만원, baseline_cost - real_housing_cost. 월세/전세 모두 항상 0
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


# ---------------------------------------------------------------------------------------------
# 더미 매물 추천 (POST /api/v1/diagnosis/listings) - 2026-09-28
# 추천 매물은 docs/samples/dummyhouses/*.csv(더미 매물)이고, 국토부 실거래가는 매물마다 붙는 "참고"
# 정보(reference_transaction)다. 기존 실거래가 기준 리포트(DiagnosisResponse)는 그대로 둔다.
# ---------------------------------------------------------------------------------------------
class BrokerInfo(BaseModel):
    """공인중개사 정보 (CSV의 공인중개사_* 컬럼)."""

    name: str = ""
    representative: str = ""
    reg_no: str = ""
    phone: str = ""
    address: str = ""
    comment: str = ""  # 공인중개사설명


class ReferenceTransaction(BaseModel):
    """이 매물 건물(지번)에서 실제로 있었던 국토부 전월세 실거래 1건 - 화면에는 "이전 실거래 내역"처럼
    보조로만 보여준다 (CSV의 참고_국토부_* 컬럼)."""

    contract_date: str = ""        # 계약일 YYYY-MM-DD
    contract_type: str = ""        # 신규/갱신
    contract_term: str = ""
    use_rr_right: str = ""         # 갱신요구권 사용 여부 Y/N
    pre_deposit: int | None = None  # 만원, 갱신 계약의 종전 보증금
    pre_monthly_rent: int | None = None
    deposit: int | None = None     # 만원
    monthly_rent: int | None = None  # 만원, 0이면 전세
    lease_kind: str = ""           # 전월세 판별 설명
    area: float | None = None
    floor: str = ""
    jibun: str = ""


class ListingRecommendation(BuildingRecommendation):
    """더미 매물 1건 단위 추천. 기존 BuildingRecommendation 필드(지도/차트/카드가 그대로 쓴다)에
    매물 상세 정보를 더한다. 기존 필드 의미: listing_deposit/listing_monthly_rent = 매물 보증금/월세,
    maintenance_fee = 이 매물의 관리비(구 평균이 아님), address = 도로명주소, deal_date = 빈 문자열,
    loan_interest = 항상 0 (보증금 한도를 넘는 매물은 대출로 메운다고 보지 않고 추천에서 제외한다).

    2026-09-28: 교통비는 비용에서 뺐다. real_housing_cost = 월세 + 관리비. 부모 클래스의 transportation_cost는 필수 필드라
    지울 수 없어 기본값 0으로 덮어쓰고 응답(JSON)에서는 내보내지 않는다(exclude). 보증금 크기까지 월 비용으로 환산한
    deposit_converted_cost(보증금전환 실질거주비)를 별도로 내려준다."""

    transportation_cost: int = Field(0, exclude=True)  # 응답에 포함하지 않는다 (교통비 삭제)
    deposit_opportunity_cost: float = 0  # 만원/월 = 보증금 x 연 전환율% / 12 (전환율은 응답의 deposit_conversion_rate)
    deposit_converted_cost: float = 0    # 만원/월 = 월세 + 관리비 + 보증금 기회비용 (보증금전환 실질거주비)

    listing_id: str = ""             # 매물등록번호 (예: SEOCHO-202609-0001)
    listing_status: str = ""         # 계약가능/계약중 (추천에는 계약가능만 나온다)
    registered_date: str = ""
    photo: str = ""                  # 내부사진. 지금은 PHOTO_PLACEHOLDER, 나중에 이미지 경로/URL
    unit_label: str = ""             # 동/호수 또는 단독·층수
    maintenance_fee_items: str = ""  # 관리비 포함 항목
    parking: str = ""
    elevator: bool | None = None
    rooms: int | None = None
    bathrooms: int | None = None
    built_year: int | None = None
    move_in_date: str = ""           # 이사가능일
    description: str = ""            # 상세설명
    broker: BrokerInfo = BrokerInfo()
    road_address: str = ""
    jibun_address: str = ""
    postal_code: str = ""
    address_source: str = ""         # 주소출처 (단독다가구는 같은 동의 실제 도로명주소를 빌려 옴)
    reference_transaction: ReferenceTransaction | None = None


class RirIncomeLevel(BaseModel):
    """소득수준별 RIR(소득 대비 주택임대료 비율)과, 그 비율을 내 월소득에 적용했을 때의 적정 월세."""

    key: str                 # "low" | "mid" | "high"
    label: str               # "하위(1-4분위)" / "중위(5-8분위)" / "상위(9-10분위)"
    rir_percent: float       # 통계 값 (%)
    affordable_rent: float   # 만원/월 = 내 월소득 x rir_percent / 100


class ListingDiagnosisResponse(BaseModel):
    affordable_rent: float        # 만원/월, 적정 월세 상한 = 내 월소득 x 수도권 RIR (docs/RIR.csv). 통계 파일이 없으면 소득의 20%(기본값)
    rent_to_income_ratio: float   # 수도권 RIR(%) - docs/RIR.csv(국토교통부 주거실태조사). 파일이 없으면 20.0(기본값)
    wolse_recommendations: list[ListingRecommendation]
    jeonse_recommendations: list[ListingRecommendation]
    used_distance_estimate: bool = False
    total_candidates: int = 0  # 조건(통근권/상태/희망가/유형)을 통과한 매물 수 - 화면에 "N건 중 상위 표시" 용
    deposit_limit: int = 0     # 만원, 이 금액을 넘는 보증금의 매물은 추천에서 제외됨 (희망 보증금, 없으면 현재 보유 보증금)
    # 보증금을 월 비용으로 환산한 이율 = 한국부동산원 수도권 전월세 전환율(종합주택) 최신 월 값 (reb_conversion_rate.py)
    deposit_conversion_rate: float = 0          # 연 %, 예: 6.35
    deposit_conversion_rate_base: str = ""      # 통계 기준 월 "2026-07"
    deposit_conversion_rate_label: str = ""     # 출처 문구
    deposit_conversion_rate_is_fallback: bool = False  # True면 조회 실패로 대체값(이전 조회값/저장값/기본값)을 쓴 것
    # 소득 대비 주택임대료 비율(RIR) 통계 (docs/RIR.csv, rir_stats.py). 파일이 없으면 비어 있다.
    rir_year: int | None = None                        # 통계 연도 (예: 2024)
    rir_source: str = ""                               # 출처
    rir_monthly_income: float = 0                      # 만원, 적정 월세 계산에 쓴 내 월소득 (입력한 연소득 / 12)
    rir_metro_affordable_rent: float | None = None     # 만원/월, 수도권 RIR을 내 월소득에 적용한 적정 월세 (= affordable_rent)
    rir_overall_percent: float | None = None           # 전국 RIR(%) - 참고
    rir_by_income: list[RirIncomeLevel] = []           # 소득수준별(하위/중위/상위) RIR과 적정 월세
    disclaimer: str = "더미 매물 데이터 기반 추정치이며, 실제 매물·시세·정책 자격과 다를 수 있습니다. 실거래가는 참고용입니다."
