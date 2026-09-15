"""
[담당: 송귀성] 주거비 산출 알고리즘 (핵심 기능)

기획서 공식: 실질 주거비 = 월세 + 관리비 + 대출이자 + 교통비 - 정부지원금

처리 흐름:
1. 소득 기준 적정 월세 상한(RIR 30% 가이드라인) 계산
2. 직장 위치 기준 통근시간 <= max_commute_minutes 인 지역만 후보로 필터링
3. 사용자의 보유 보증금과 지역 기준 보증금(base_deposit)을 비교하여
   - 보증금이 충분하면: 전월세전환율로 월세를 낮춰줌 (surplus 전환)
   - 보증금이 부족하면: 부족분(shortfall)을 대출로 가정하고 대출이자를 계산
4. 정책 매칭 결과(policy_matcher)로 정부지원금을 반영
5. 실질 주거비가 낮은 순으로 정렬하여 추천 리포트 생성
"""
import json
import logging
from dataclasses import dataclass

from app.core.config import settings
from app.services import api_collector, policy_matcher

logger = logging.getLogger(__name__)

MARKET_LOAN_RATE_ANNUAL_PERCENT = 4.5  # 일반 전세자금대출 금리 (참고용 샘플)


@dataclass
class RegionData:
    name: str
    base_rent: int
    maintenance_fee: int
    commute_minutes: dict[str, int]
    data_source: str = "샘플 데이터"  # MOLIT_API_KEY로 실거래가 조회 성공 시 "국토부 실거래가"로 바뀐다


def _load_lawd_codes() -> dict[str, str]:
    with open(settings.lawd_codes_file, encoding="utf-8") as f:
        return json.load(f)["regions"]


def _load_regions() -> tuple[list[RegionData], float, int, list[str]]:
    with open(settings.regions_file, encoding="utf-8") as f:
        raw = json.load(f)

    lawd_codes = _load_lawd_codes() if api_collector.is_enabled() else {}

    regions = []
    for r in raw["regions"]:
        base_rent = r["base_rent"]
        data_source = "샘플 데이터"

        # MOLIT_API_KEY가 설정되어 있으면 국토교통부 실거래가로 base_rent를 대체 시도.
        # API 호출이 실패하거나(키 미승인, 네트워크 오류 등) 해당 지역 실거래 내역이 없으면
        # 조용히 샘플 값으로 폴백한다 (api_collector가 warning 로그만 남기고 None을 반환).
        lawd_cd = lawd_codes.get(r["name"])
        if lawd_cd:
            summary = api_collector.summarize_region_rent(lawd_cd)
            if summary:
                base_rent = summary["base_rent"]
                data_source = f"국토부 실거래가 (최근 {summary['sample_size']}건 평균)"

        regions.append(RegionData(
            name=r["name"],
            base_rent=base_rent,
            maintenance_fee=r["maintenance_fee"],
            commute_minutes=r["commute_minutes"],
            data_source=data_source,
        ))

    return regions, raw["conversion_rate_annual_percent"], raw["base_deposit"], raw["work_hubs"]


def _transportation_cost(commute_minutes: int) -> int:
    """통근시간 구간별 월 교통비(만원) 추정치. (정기권 기준 샘플)"""
    if commute_minutes <= 20:
        return 6
    if commute_minutes <= 30:
        return 7
    if commute_minutes <= 40:
        return 9
    return 11


def calculate_affordable_rent(monthly_income: int) -> int:
    """소득 대비 주거비 비율(RIR) 30% 가이드라인 기준 적정 월세 상한."""
    return round(monthly_income * 0.30)


def get_work_hubs() -> list[str]:
    _, _, _, hubs = _load_regions()
    return hubs


def run_diagnosis(request) -> dict:
    """진단 실행: 후보 지역 필터링 -> 비용 계산 -> 정책 매칭 -> 정렬."""
    regions, conversion_rate_annual, base_deposit, work_hubs = _load_regions()

    if request.work_location not in work_hubs:
        raise ValueError(
            f"지원하지 않는 직장 위치입니다: '{request.work_location}'. "
            f"현재 지원 지역: {', '.join(work_hubs)} (추후 카카오맵/SGIS API 연동 시 전국 확장 예정)"
        )

    affordable_rent = calculate_affordable_rent(request.monthly_income)
    rir = round((affordable_rent / request.monthly_income) * 100, 1) if request.monthly_income else 0.0

    conversion_rate_monthly = conversion_rate_annual / 12 / 100
    market_loan_rate_monthly = MARKET_LOAN_RATE_ANNUAL_PERCENT / 12 / 100

    # 정책 자격 사전 판단 (대출 금리 우대 여부에 사용)
    policy_loan = policy_matcher.find_eligible_loan_policy(request)
    loan_rate_monthly = (
        (policy_loan["loan_rate_annual_percent"] / 12 / 100) if policy_loan else market_loan_rate_monthly
    )

    results = []
    for region in regions:
        commute = region.commute_minutes.get(request.work_location)
        if commute is None or commute > request.max_commute_minutes:
            continue

        # 1) 보증금 반영 (전환 또는 대출)
        deposit_gap = base_deposit - request.deposit
        if deposit_gap <= 0:
            surplus = -deposit_gap
            rent = max(0, region.base_rent - round(surplus * conversion_rate_monthly))
            loan_interest = 0
        else:
            rent = region.base_rent
            loan_interest = round(deposit_gap * loan_rate_monthly)

        maintenance_fee = region.maintenance_fee
        transportation_cost = _transportation_cost(commute)

        # 2) 정책 매칭 (월세 지원금 등)
        matched_policies = policy_matcher.match_policies(request, rent=rent, policy_loan=policy_loan)
        government_support = sum(p["monthly_benefit"] for p in matched_policies)

        real_housing_cost = rent + maintenance_fee + loan_interest + transportation_cost - government_support

        # 3) 비교 기준(baseline): 정책/보증금 최적화 없이 희망월세 그대로 살았을 때 비용
        baseline_rent = request.desired_rent if request.desired_rent is not None else region.base_rent
        baseline_loan_interest = round(deposit_gap * market_loan_rate_monthly) if deposit_gap > 0 else 0
        baseline_cost = baseline_rent + maintenance_fee + baseline_loan_interest + transportation_cost

        monthly_savings = baseline_cost - real_housing_cost

        results.append(
            {
                "region": region.name,
                "commute_minutes": commute,
                "rent": rent,
                "maintenance_fee": maintenance_fee,
                "loan_interest": loan_interest,
                "transportation_cost": transportation_cost,
                "government_support": government_support,
                "real_housing_cost": real_housing_cost,
                "baseline_cost": baseline_cost,
                "monthly_savings": monthly_savings,
                "matched_policies": matched_policies,
                "data_source": region.data_source,
            }
        )

    # data_analysis.py(pandas)에서 실질 주거비 오름차순 정렬 + 상위 N개 선별
    from app.services import data_analysis

    top_results = data_analysis.rank_by_real_cost(results, top_n=5)

    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "recommendations": top_results,
    }
