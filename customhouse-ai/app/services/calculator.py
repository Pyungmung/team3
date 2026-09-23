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
5. 정책 매칭(policy_matcher)으로 매물 지역·사용자 조건에 맞는 정책을 찾는다.
   2026-09-17: 실제 정책 데이터(docs/housing_policy_list.csv)는 지원혜택이 자유
   텍스트라 정형 수치가 없어 government_support는 항상 0 - "주거정책 추천" 표에
   보여주는 정보성 매칭일 뿐, 아래 공식의 정부지원금 항목에는 반영하지 않는다.
6. data_analysis.rank_by_real_cost가 "기존 예상 주거비보다 실제로 더 저렴한" 매물만 골라
   실질 주거비 오름차순 상위 N개를 추천 리스트로 반환한다.
"""
import json
import logging
import math
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

from app.core.config import settings
from app.services import api_collector, kakao_mobility, kakao_routing, policy_matcher

logger = logging.getLogger(__name__)

MARKET_LOAN_RATE_ANNUAL_PERCENT = 4.5  # 일반 전세자금대출 금리 (참고용 샘플)

# 월세 매물 중 "보증금 / 월세" 비율이 이 값 이상이면 "반전세형"으로 표시한다. 국토부 실거래
# 데이터에는 보증금은 전세 수준으로 크게 걸고 월세는 몇만원만 얹는 "반전세" 거래가 실제로 섞여
# 있는데, 일반적인 "보증금 조금 + 월세 시세대로" 매물과 성격이 달라 구분 없이 보여주면 "월세가
# 이상하게 낮다"는 오해를 준다. 보증금 절대액만으로는 큰 평형의 정상적인 고액 월세(예: 보증금
# 1억/월세 160만원, 비율 62.5)까지 반전세로 오분류하게 되어, 실제 데이터로 비율 분포를 확인해본
# 결과 일반 매물은 대략 비율 4~200대, 반전세형은 1500대 이상으로 뚜렷하게 갈렸다.
SEMI_JEONSE_RATIO_THRESHOLD = 300

# 통근시간 추정 공식(직선거리 기반) 계수. "기본 소요시간(도보+대기+환승) + 거리당 이동시간"
# 형태로, 기존에 손으로 채워뒀던 18개 지역 x 6개 직장군 샘플 통근시간표와 오차가 크지 않도록
# 역산해서 맞춘 값이다 (서울 지하철/버스 평균 실효 속도 감안, 2.2분/km ≈ 시속 27km대).
COMMUTE_BASE_MINUTES = 10
COMMUTE_MINUTES_PER_KM = 2.2


@dataclass
class RegionMeta:
    name: str
    base_rent: int
    base_deposit: int  # 샘플 폴백용 (region.json 전체 base_deposit 재사용)
    maintenance_fee: int
    lat: float
    lon: float
    lawd_cds: list[str]


def _load_lawd_codes() -> dict[str, list[str]]:
    with open(settings.lawd_codes_file, encoding="utf-8") as f:
        return json.load(f)["regions"]


def _load_region_coords() -> dict[str, list[float]]:
    with open(settings.region_coords_file, encoding="utf-8") as f:
        return json.load(f)["regions"]


def _load_work_locations() -> dict[str, dict[str, float]]:
    """직장 위치(work_hubs) 이름 -> {lat, lon}. 서울 25개 자치구 전체 + 분당구."""
    with open(settings.work_locations_file, encoding="utf-8") as f:
        raw = json.load(f)["work_hubs"]
    return {w["name"]: {"lat": w["lat"], "lon": w["lon"]} for w in raw}


def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0  # 지구 반지름(km)
    p1, p2 = math.radians(lat1), math.radians(lat2)
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = math.sin(d_lat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(d_lon / 2) ** 2
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _estimate_commute_minutes(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    """직선거리 기반 통근시간 추정치 (샘플 추정치). 카카오모빌리티 길찾기 API가 지원하지 않는
    대중교통/도보 수단이거나, KAKAO_REST_APP_KEY 미설정/호출 실패 시 폴백으로 쓴다."""
    distance_km = _haversine_km(lat1, lon1, lat2, lon2)
    return round(COMMUTE_BASE_MINUTES + distance_km * COMMUTE_MINUTES_PER_KM)


def _commute_minutes(
    work_lat: float, work_lon: float, dest_lat: float, dest_lon: float, transport_type: str | None
) -> tuple[int, str]:
    """통근시간(분)과 산출 방식을 함께 돌려준다.

    "주요 통근 수단"(transport_type)에 맞는 카카오 API로 실제 경로 소요시간을 구한다.
    - CAR: 카카오모빌리티 길찾기(자동차 전용, kakao_mobility.py)
    - PUBLIC: 카카오맵 대중교통 경로 조회 (여러 경로 중 가장 빠른 것)
    - WALK: 카카오맵 도보 경로 조회 (목적지가 너무 멀면 결과 없음)
    API 키 미설정/호출 실패/결과 없음(예: 도보로 가기엔 너무 먼 거리)이면 기존 직선거리
    추정치로 폴백한다.
    """
    if transport_type == "CAR" and kakao_mobility.is_enabled():
        try:
            minutes = kakao_mobility.fetch_car_commute_minutes(dest_lat, dest_lon, work_lat, work_lon)
            return minutes, "카카오 길찾기 API(자동차)"
        except kakao_mobility.KakaoMobilityError as e:
            logger.warning(str(e))
    elif transport_type == "PUBLIC" and kakao_routing.is_enabled():
        try:
            minutes = kakao_routing.fetch_public_transit_minutes(dest_lat, dest_lon, work_lat, work_lon)
            return minutes, "카카오맵 API(대중교통)"
        except kakao_routing.KakaoRoutingError as e:
            logger.warning(str(e))
    elif transport_type == "WALK" and kakao_routing.is_enabled():
        try:
            minutes = kakao_routing.fetch_walk_minutes(dest_lat, dest_lon, work_lat, work_lon)
            return minutes, "카카오맵 API(도보)"
        except kakao_routing.KakaoRoutingError as e:
            logger.warning(str(e))

    return _estimate_commute_minutes(dest_lat, dest_lon, work_lat, work_lon), "직선거리 추정"


def _load_regions() -> tuple[list[RegionMeta], float]:
    with open(settings.regions_file, encoding="utf-8") as f:
        raw = json.load(f)

    lawd_codes = _load_lawd_codes()
    region_coords = _load_region_coords()

    regions = []
    for r in raw["regions"]:
        coord = region_coords.get(r["name"])
        if not coord:
            logger.warning(f"region_coords.json에 좌표가 없는 지역이라 건너뜁니다: {r['name']}")
            continue
        regions.append(
            RegionMeta(
                name=r["name"],
                base_rent=r["base_rent"],
                base_deposit=raw["base_deposit"],
                maintenance_fee=r["maintenance_fee"],
                lat=coord[0],
                lon=coord[1],
                lawd_cds=lawd_codes.get(r["name"], []),
            )
        )

    return regions, raw["conversion_rate_annual_percent"]


def _candidate_buildings(region: RegionMeta) -> list[dict]:
    """지역의 후보 매물(건물) 목록을 구한다.

    실거래 데이터가 있으면 그걸 쓰고, DATA_GO_KR_API_KEY 미설정이거나 해당 지역에
    실거래 내역이 없으면(신고 지연 등) 지역 평균 샘플값으로 만든 가상 월세 매물 1건으로 폴백한다
    (샘플 데이터에는 전세 시세가 따로 없어 전세 추천은 폴백 시 빈 목록이 된다).

    주의: 예전엔 성능을 위해 "월세 오름차순 정렬 후 상위 N개"로 후보를 미리 잘랐는데, 이러면
    보증금이 아주 크고 월세는 몇만원뿐인 반전세형 매물만 남고, 정작 흔한 월세 매물(예: 서울
    평균대인 월 80만원대)은 계산에 들어가지도 못하는 편향이 생겼다 ("월세가 너무 낮게 보인다"는
    피드백의 원인). 그래서 지금은 자르지 않고 지역의 모든 후보를 그대로 계산에 반영한다.
    """
    buildings: list[dict] = []
    if region.lawd_cds and api_collector.is_enabled():
        buildings = api_collector.fetch_candidate_buildings(tuple(region.lawd_cds), region.name)

    if not buildings:
        return [
            {
                "region": region.name,
                "property_type": "",
                "building_name": f"{region.name} 평균 시세 (샘플)",
                "unnamed": False,
                "dong": "",
                "deposit": region.base_deposit,
                "monthly_rent": region.base_rent,
                "lease_type": "월세",
                "exclusive_area": None,
                "floor": "",
                "deal_date": "",
            }
        ]

    return buildings


def _transportation_cost(commute_minutes: int) -> int:
    """통근시간 구간별 월 교통비(만원) 추정치. (정기권 기준 샘플)
    COMMUTE_BASE_MINUTES가 10이라 10분은 "직장 바로 근처(도보권)"에 해당하는 최소치라,
    도보·자전거로 볼 수 있게 20분 이하 구간과 분리해 더 낮은 교통비를 매긴다."""
    if commute_minutes <= 10:
        return 3
    if commute_minutes <= 20:
        return 6
    if commute_minutes <= 30:
        return 7
    if commute_minutes <= 40:
        return 9
    return 11


def calculate_affordable_rent(monthly_income: float) -> int:
    """소득 대비 주거비 비율(RIR) 30% 가이드라인 기준 적정 월세 상한."""
    return round(monthly_income * 0.30)


def get_work_hubs() -> list[str]:
    return list(_load_work_locations().keys())


def run_diagnosis(request) -> dict:
    """진단 실행: 후보 지역 필터링 -> 매물별 비용 계산 -> 정책 매칭 -> 정렬."""
    regions, conversion_rate_annual = _load_regions()

    # work_lat/work_lon(카카오 주소 검색으로 얻은 정확한 좌표)이 오면 그 좌표를 그대로 쓴다 -
    # 구 단위 드롭다운(26개 고정 지점)으로 스냅하지 않고, 실제 회사 주소 지점부터 통근시간을
    # 계산할 수 있다. 안 오면(드롭다운으로 선택한 경우) 기존처럼 work_location 이름으로 조회한다.
    if request.work_lat is not None and request.work_lon is not None:
        work_lat, work_lon = request.work_lat, request.work_lon
    else:
        work_locations = _load_work_locations()
        work_location = work_locations.get(request.work_location)
        if work_location is None:
            raise ValueError(
                f"지원하지 않는 직장 위치입니다: '{request.work_location}'. "
                f"현재 지원 지역: {', '.join(work_locations.keys())}"
            )
        work_lat, work_lon = work_location["lat"], work_location["lon"]

    effective_monthly_income = request.effective_monthly_income
    affordable_rent = calculate_affordable_rent(effective_monthly_income)
    rir = round((affordable_rent / effective_monthly_income) * 100, 1) if effective_monthly_income else 0.0

    # 희망 보증금/전세액(desired_deposit)이 있으면 그걸 "실제 계약에 쓸 금액"으로 보고 보증금
    # 전환/대출 계산에 쓴다 (대출 등을 더해 현재 보유 보증금보다 클 수 있음). 없으면 현재 보유
    # 보증금(deposit) 그대로 쓴다.
    effective_deposit = request.desired_deposit if request.desired_deposit is not None else request.deposit

    # 희망 보증금/전세액(desired_deposit)이 있으면 그걸 "실제 계약에 쓸 금액"으로 보고 보증금
    # 전환/대출 계산에 쓴다 (대출 등을 더해 현재 보유 보증금보다 클 수 있음). 없으면 현재 보유
    # 보증금(deposit) 그대로 쓴다.
    effective_deposit = request.desired_deposit if request.desired_deposit is not None else request.deposit

    conversion_rate_monthly = conversion_rate_annual / 12 / 100
    market_loan_rate_monthly = MARKET_LOAN_RATE_ANNUAL_PERCENT / 12 / 100

    results = []
    seen_buildings: set[tuple] = set()

    # 선호 주택 유형: 일부 유형만 선택했으면 그 유형의 매물만 추천한다. 비어 있거나 4종을 다 골랐거나
    # 알 수 없는 값뿐이면 필터를 걸지 않는다. (샘플 폴백 매물은 유형이 없어서 필터가 걸리면 제외된다.)
    all_types = set(api_collector.PROPERTY_TYPES)
    selected_types = set(request.preferred_building_types) & all_types
    type_filter = selected_types if selected_types and selected_types != all_types else None

    # 통근권 안에 드는 지역만 먼저 추린다. 자차(CAR) 모드는 지역마다 카카오모빌리티 API를
    # 호출해야 해서(네트워크 호출) 순차로 하면 느려지므로, 실거래 조회와 마찬가지로 스레드풀로
    # 동시에 계산한다.
    def _region_commute(region: RegionMeta) -> tuple[int, str]:
        return _commute_minutes(work_lat, work_lon, region.lat, region.lon, request.transport_type)

    with ThreadPoolExecutor(max_workers=16) as pool:
        commute_results = list(pool.map(_region_commute, regions))

    eligible = [
        (region, minutes, source)
        for region, (minutes, source) in zip(regions, commute_results)
        if minutes <= request.max_commute_minutes
    ]

    with ThreadPoolExecutor(max_workers=16) as pool:
        candidates_by_region = dict(
            zip((r.name for r, _, _ in eligible), pool.map(_candidate_buildings, (r for r, _, _ in eligible)))
        )

    for region, commute, commute_source in eligible:
        transportation_cost = _transportation_cost(commute)

        for building in candidates_by_region[region.name]:
            if type_filter and building["property_type"] not in type_filter:
                continue

            # 희망 보증금/전세액·희망 월세를 입력했으면 "그 금액 이하"인 매물만 검색 대상으로
            # 삼는다 (전세는 월세가 항상 0이라 희망 월세 필터는 자동으로 통과된다).
            if request.desired_deposit is not None and building["deposit"] > request.desired_deposit:
                continue
            if request.desired_rent is not None and building["monthly_rent"] > request.desired_rent:
                continue

            dedup_key = (region.name, building["building_name"], building["dong"])
            if building["unnamed"]:
                # 건물명 없는 매물(단독다가구 등)은 이름이 "동네+형태"로 같아서, 그대로 두면 동네당
                # 1건만 남는다. 가격/면적까지 같아야 같은 매물로 본다.
                dedup_key += (building["deposit"], building["monthly_rent"], building["exclusive_area"])
            if dedup_key in seen_buildings:
                continue
            seen_buildings.add(dedup_key)

            # 1) 매물 실제 보증금 반영 (전환 또는 대출)
            deposit_gap = building["deposit"] - effective_deposit
            if deposit_gap <= 0:
                surplus = -deposit_gap
                rent = max(0, building["monthly_rent"] - round(surplus * conversion_rate_monthly))
                loan_interest = 0
            else:
                rent = building["monthly_rent"]
                loan_interest = round(deposit_gap * market_loan_rate_monthly)

            maintenance_fee = region.maintenance_fee

            # 2) 정책 매칭 - "주거정책 추천" 표에 보여줄 정보성 매칭 (매물 지역 기준).
            # CSV 실데이터는 지원혜택이 자유 텍스트라 정형 수치가 없어 government_support는
            # 항상 0 - policy_matcher.py 모듈 docstring 참고.
            matched_policies = policy_matcher.match_display_policies(request, building_region=region.name)
            government_support = 0

            real_housing_cost = rent + maintenance_fee + loan_interest + transportation_cost - government_support

            # 3) 비교 기준(baseline): 정책/보증금 최적화 없이 그 매물 원래 월세 그대로 살았을 때 비용
            baseline_rent = request.desired_rent if request.desired_rent is not None else building["monthly_rent"]
            baseline_loan_interest = round(deposit_gap * market_loan_rate_monthly) if deposit_gap > 0 else 0
            baseline_cost = baseline_rent + maintenance_fee + baseline_loan_interest + transportation_cost

            monthly_savings = baseline_cost - real_housing_cost

            is_real_listing = bool(building["deal_date"])
            data_source = f"국토부 실거래가 ({building['deal_date']} 거래)" if is_real_listing else "샘플 데이터"

            # 월세인데 보증금/월세 비율이 유난히 큰 "반전세형" 매물은 배지로 구분 표시한다
            # (전세는 애초에 lease_type으로 이미 구분되니 여기선 해당 없음).
            is_semi_jeonse = (
                building["lease_type"] == "월세"
                and building["monthly_rent"] > 0
                and building["deposit"] / building["monthly_rent"] >= SEMI_JEONSE_RATIO_THRESHOLD
            )

            results.append(
                {
                    "region": region.name,
                    "property_type": building["property_type"],
                    "building_name": building["building_name"],
                    "dong": building["dong"],
                    "exclusive_area": building["exclusive_area"],
                    "floor": building["floor"],
                    "deal_date": building["deal_date"],
                    "lease_type": building["lease_type"],  # "전세" | "월세"
                    "is_semi_jeonse": is_semi_jeonse,  # 월세인데 보증금이 커서 반전세 성격인 매물
                    "commute_minutes": commute,
                    "commute_source": commute_source,  # "카카오 길찾기 API(자동차)" 또는 "직선거리 추정"
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

    # data_analysis.py(pandas)에서 절감액이 있는 매물만 골라 실질 주거비 오름차순으로 상위 N개 선별.
    # 전세/월세는 실질 주거비의 구성이 달라(전세는 월세=0) 한 목록에 섞으면 비교가 이상해지므로
    # lease_type별로 따로 순위를 매겨 두 개의 추천 목록으로 나눈다.
    from app.services import data_analysis

    wolse_results = [r for r in results if r["lease_type"] == "월세"]
    jeonse_results = [r for r in results if r["lease_type"] == "전세"]

    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "wolse_recommendations": data_analysis.rank_by_real_cost(wolse_results, top_n=25),
        "jeonse_recommendations": data_analysis.rank_by_real_cost(jeonse_results, top_n=25),
    }
