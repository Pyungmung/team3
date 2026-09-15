"""
[담당: 송귀성] 주거비 산출 알고리즘 (핵심 기능)

기획서 공식: 실질 주거비 = 월세 + 관리비 + 대출이자 + 교통비 - 정부지원금

처리 흐름 (2026-09-15: 지역 평균 추천 -> 개별 매물(건물) 추천으로 변경):
1. 소득 기준 적정 월세 상한(RIR 30% 가이드라인) 계산
2. 직장 위치 기준 통근시간 <= max_commute_minutes 인 지역만 후보로 필터링
3. 후보 지역마다 국토부 실거래 개별 건물을 후보 매물로 가져옴
   (DATA_GO_KR_API_KEY 미설정이거나 해당 지역에 실거래 내역이 없으면
    지역 평균 샘플값으로 만든 가상 매물 1건으로 폴백)
4. 매물마다 "그 매물 실제 보증금"과 사용자의 보유 보증금을 비교하여
   - 보증금이 충분하면: 전월세전환율로 월세를 낮춰줌 (surplus 전환)
   - 보증금이 부족하면: 부족분(shortfall)을 대출로 가정하고 대출이자를 계산
5. 정책 매칭 결과(policy_matcher)로 정부지원금을 반영
6. data_analysis.rank_by_real_cost가 "기존 예상 주거비보다 실제로 더 저렴한" 매물만 골라
   실질 주거비 오름차순 상위 N개를 추천 리스트로 반환한다.
"""
import json
import logging
from dataclasses import dataclass

from app.core.config import settings
from app.services import api_collector, policy_matcher

logger = logging.getLogger(__name__)

MARKET_LOAN_RATE_ANNUAL_PERCENT = 4.5  # 일반 전세자금대출 금리 (참고용 샘플)
MAX_CANDIDATES_PER_REGION = 15  # 지역당 검토할 매물 수 상한 (성능/정책매칭 호출 수 제한)


@dataclass
class RegionMeta:
    name: str
    base_rent: int
    base_deposit: int  # 샘플 폴백용 (region.json 전체 base_deposit 재사용)
    maintenance_fee: int
    commute_minutes: dict[str, int]
    lawd_cds: list[str]


def _load_lawd_codes() -> dict[str, list[str]]:
    with open(settings.lawd_codes_file, encoding="utf-8") as f:
        return json.load(f)["regions"]


def _load_regions() -> tuple[list[RegionMeta], float, list[str]]:
    with open(settings.regions_file, encoding="utf-8") as f:
        raw = json.load(f)

    lawd_codes = _load_lawd_codes()

    regions = [
        RegionMeta(
            name=r["name"],
            base_rent=r["base_rent"],
            base_deposit=raw["base_deposit"],
            maintenance_fee=r["maintenance_fee"],
            commute_minutes=r["commute_minutes"],
            lawd_cds=lawd_codes.get(r["name"], []),
        )
        for r in raw["regions"]
    ]

    return regions, raw["conversion_rate_annual_percent"], raw["work_hubs"]


def _candidate_buildings(region: RegionMeta) -> list[dict]:
    """지역의 후보 매물(건물) 목록을 구한다.

    실거래 데이터가 있으면 그걸 쓰고, DATA_GO_KR_API_KEY 미설정이거나 해당 지역에
    실거래 내역이 없으면(신고 지연 등) 지역 평균 샘플값으로 만든 가상 매물 1건으로 폴백한다.
    월세가 싼 순으로 먼저 잘라둬야 아래에서 전부 계산기를 돌리지 않아도 된다
    (실제 주거비는 결국 월세에 지배적으로 좌우되므로, 애초에 비싼 매물은 후보에서 제외).
    """
    buildings: list[dict] = []
    if region.lawd_cds and api_collector.is_enabled():
        buildings = api_collector.fetch_candidate_buildings(tuple(region.lawd_cds), region.name)

    if not buildings:
        return [
            {
                "region": region.name,
                "building_name": f"{region.name} 평균 시세 (샘플)",
                "dong": "",
                "deposit": region.base_deposit,
                "monthly_rent": region.base_rent,
                "exclusive_area": None,
                "floor": "",
                "deal_date": "",
            }
        ]

    return sorted(buildings, key=lambda b: b["monthly_rent"])[:MAX_CANDIDATES_PER_REGION]


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
    _, _, hubs = _load_regions()
    return hubs


def run_diagnosis(request) -> dict:
    """진단 실행: 후보 지역 필터링 -> 매물별 비용 계산 -> 정책 매칭 -> 정렬."""
    regions, conversion_rate_annual, work_hubs = _load_regions()

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
    seen_buildings: set[tuple[str, str, str]] = set()

    for region in regions:
        commute = region.commute_minutes.get(request.work_location)
        if commute is None or commute > request.max_commute_minutes:
            continue

        transportation_cost = _transportation_cost(commute)

        for building in _candidate_buildings(region):
            dedup_key = (region.name, building["building_name"], building["dong"])
            if dedup_key in seen_buildings:
                continue
            seen_buildings.add(dedup_key)

            # 1) 매물 실제 보증금 반영 (전환 또는 대출)
            deposit_gap = building["deposit"] - request.deposit
            if deposit_gap <= 0:
                surplus = -deposit_gap
                rent = max(0, building["monthly_rent"] - round(surplus * conversion_rate_monthly))
                loan_interest = 0
            else:
                rent = building["monthly_rent"]
                loan_interest = round(deposit_gap * loan_rate_monthly)

            maintenance_fee = region.maintenance_fee

            # 2) 정책 매칭 (월세 지원금 등)
            matched_policies = policy_matcher.match_policies(request, rent=rent, policy_loan=policy_loan)
            # 지원금이 실제로 내는 돈보다 많을 수는 없다 (예: 보증금 전환으로 월세가 5만원까지 떨어진
            # 매물에 정액 20만원 청년월세지원이 그대로 붙으면 실질 주거비가 음수(-1만원)가 되는 버그가 있었음).
            actual_cost_before_support = rent + maintenance_fee + loan_interest + transportation_cost
            government_support = min(
                sum(p["monthly_benefit"] for p in matched_policies), actual_cost_before_support
            )

            real_housing_cost = actual_cost_before_support - government_support

            # 3) 비교 기준(baseline): 정책/보증금 최적화 없이 그 매물 원래 월세 그대로 살았을 때 비용
            baseline_rent = request.desired_rent if request.desired_rent is not None else building["monthly_rent"]
            baseline_loan_interest = round(deposit_gap * market_loan_rate_monthly) if deposit_gap > 0 else 0
            baseline_cost = baseline_rent + maintenance_fee + baseline_loan_interest + transportation_cost

            monthly_savings = baseline_cost - real_housing_cost

            is_real_listing = bool(building["deal_date"])
            data_source = f"국토부 실거래가 ({building['deal_date']} 거래)" if is_real_listing else "샘플 데이터"

            results.append(
                {
                    "region": region.name,
                    "building_name": building["building_name"],
                    "dong": building["dong"],
                    "exclusive_area": building["exclusive_area"],
                    "floor": building["floor"],
                    "deal_date": building["deal_date"],
                    "commute_minutes": commute,
                    "listing_deposit": building["deposit"],       # 그 매물의 실제 보증금 (실거래가 원본)
                    "listing_monthly_rent": building["monthly_rent"],  # 그 매물의 실제 월세 (실거래가 원본, 보증금 전환 적용 전)
                    "rent": rent,
                    "maintenance_fee": maintenance_fee,
                    "loan_interest": loan_interest,
                    "transportation_cost": transportation_cost,
                    "government_support": government_support,
                    "real_housing_cost": real_housing_cost,
                    "baseline_cost": baseline_cost,
                    "monthly_savings": monthly_savings,
                    "matched_policies": matched_policies,
                    "data_source": data_source,
                }
            )

    # data_analysis.py(pandas)에서 절감액이 있는 매물만 골라 실질 주거비 오름차순으로 상위 N개 선별
    from app.services import data_analysis

    top_results = data_analysis.rank_by_real_cost(results, top_n=5)

    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "recommendations": top_results,
    }
