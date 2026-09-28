"""
[담당: 송귀성] 더미 매물 기반 추천 (POST /api/v1/diagnosis/listings)

기존 calculator.run_diagnosis(국토부 실거래가를 추천 매물로 사용)는 그대로 두고, 같은 입력으로
docs/samples/dummyhouses/*.csv 의 더미 매물을 추천한다. 국토부 실거래가는 추천 대상이 아니라 매물마다
붙는 "참고 실거래"(reference_transaction)로만 내려준다.

처리 흐름 (통근시간/교통비/정책/순위는 calculator·policy_matcher·data_analysis를 그대로 재사용):
1. 직장 좌표, 소득 기준 적정 월세(RIR 30%)
2. 자치구 대표좌표 기준 1차 통근권 필터 (이동수단별 버퍼, REGION_PREFILTER_BUFFER_MIN_BY_MODE)
3. 통근권 자치구의 CSV 매물 -> 계약가능만, 선호 유형, 보증금 한도 이하, 희망 월세 이하, 이사희망시기 필터.
   보증금 한도 = 희망 보증금(전세액)이 있으면 그 값, 없으면 현재 보유 보증금. 한도를 넘는 매물은 추천하지 않는다
   (2026-09-28: 예전엔 부족한 보증금을 대출로 채운다고 가정해 이자를 비용에 더했는데, 보증금이 부족한 매물을
   추천하는 게 이상하다는 지적으로 대출이자 계산을 없애고 한도 필터로 바꿨다)
4. 비용 계산: 월세는 CSV 월세 그대로, 관리비는 그 매물의 관리비. 실질 주거비 = 월세 + 관리비 + 교통비
   (대출이자 항목은 응답 호환을 위해 남기되 항상 0)
5. 월세/전세 따로 실질 주거비 낮은 순으로 상위 TOP_N (지역별 비례 배분)
6. 최종 목록만 매물 자신의 좌표로 통근시간을 정확히 다시 계산(CSV에 좌표가 이미 있어 주소/좌표 외부 API
   호출은 없다)해서 희망 통근시간을 넘는 매물 제외, 교통비/실질 주거비 갱신 후 재정렬
"""
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date

from app.services import calculator, data_analysis, listing_repository, listing_schema, policy_matcher

logger = logging.getLogger(__name__)

TOP_N = 1000  # 월세/전세 각각 최대 추천 수 (지도 마커/카드 렌더링이 버틸 수 있는 상한)

# 희망 이사 일정 -> 이사가능일이 오늘로부터 며칠 이내여야 하는지. 그 외(WITHIN_6M/EXPLORING/미지정)는 제한 없음.
MOVE_SCHEDULE_MAX_DAYS = {"IMMEDIATE": 30, "WITHIN_3M": 90}


def _resolve_work_coords(request) -> tuple[float, float]:
    """calculator.run_diagnosis와 같은 규칙: 정확한 좌표(work_lat/lon)가 오면 그대로, 아니면 이름으로 조회."""
    if request.work_lat is not None and request.work_lon is not None:
        return request.work_lat, request.work_lon
    work_locations = calculator._load_work_locations()
    work_location = work_locations.get(request.work_location)
    if work_location is None:
        raise ValueError(
            f"지원하지 않는 직장 위치입니다: '{request.work_location}'. "
            f"현재 지원 지역: {', '.join(work_locations.keys())}"
        )
    return work_location["lat"], work_location["lon"]


def _move_in_ok(listing: dict, move_schedule: str | None, today: date) -> bool:
    max_days = MOVE_SCHEDULE_MAX_DAYS.get(move_schedule or "")
    if max_days is None:
        return True
    try:
        move_in = date.fromisoformat(listing["move_in_date"])
    except (TypeError, ValueError):
        return True  # 이사가능일 정보가 없거나 형식이 다르면 걸러내지 않는다
    return (move_in - today).days <= max_days  # 과거 날짜는 "지금 바로 입주 가능"으로 본다


def _clean_nan(record: dict) -> dict:
    """rank_by_real_cost가 pandas DataFrame을 거치면서 None인 숫자 칸이 NaN(float)이 된다.
    NaN은 JSON 직렬화가 실패하므로 None으로 되돌린다."""
    for key, value in record.items():
        if isinstance(value, float) and value != value:
            record[key] = None
    return record


def _to_result(listing: dict, region_commute: int, commute_source: str, transportation_cost: int,
               matched_policies: list[dict]) -> dict:
    rent = listing["monthly_rent"] or 0
    deposit = listing["deposit"] or 0
    maintenance_fee = listing["maintenance_fee"] or 0
    loan_interest = 0  # 보증금 부족분 대출이자는 계산하지 않는다 - 보증금 한도를 넘는 매물을 아예 추천하지 않는다
    real_housing_cost = rent + maintenance_fee + loan_interest + transportation_cost
    return {
        # --- 기존 BuildingRecommendation 필드 (지도/차트/카드 공용) ---
        "region": listing["region"],
        "property_type": listing["property_type"],
        "building_name": listing["building_name"],
        "dong": listing["dong"],
        "address": listing["road_address"] or listing["jibun_address"],
        "lat": listing["lat"],
        "lon": listing["lon"],
        "exclusive_area": listing["exclusive_area"],
        "floor": listing["floor"],
        "deal_date": "",  # 더미 매물은 실거래 기준월이 없다 (등록일은 registered_date)
        "lease_type": listing["lease_type"],
        "is_semi_jeonse": bool(listing["is_semi_jeonse"]) and listing["lease_type"] == "월세",
        "commute_minutes": region_commute,
        "commute_source": commute_source,
        "listing_deposit": deposit,
        "listing_monthly_rent": rent,
        "rent": rent,
        "maintenance_fee": maintenance_fee,
        "loan_interest": loan_interest,
        "transportation_cost": transportation_cost,
        "government_support": 0,
        "real_housing_cost": real_housing_cost,
        "baseline_cost": real_housing_cost,  # 비교할 별도 기준이 없다 (월세/전세 모두 절감액 0)
        "monthly_savings": 0,
        "matched_policies": matched_policies,
        "data_source": f"더미 매물 (등록 {listing['registered_date']})",
        # --- 더미 매물 전용 필드 ---
        "listing_id": listing["listing_id"],
        "listing_status": listing["listing_status"],
        "registered_date": listing["registered_date"],
        "photo": listing["photo"],
        "unit_label": listing["unit_label"],
        "maintenance_fee_items": listing["maintenance_fee_items"],
        "parking": listing["parking"],
        "elevator": listing["elevator"],
        "rooms": listing["rooms"],
        "bathrooms": listing["bathrooms"],
        "built_year": listing["built_year"],
        "move_in_date": listing["move_in_date"],
        "description": listing["description"],
        "road_address": listing["road_address"],
        "jibun_address": listing["jibun_address"],
        "postal_code": listing["postal_code"],
        "address_source": listing["address_source"],
    }


def _attach_details(record: dict, listing: dict) -> dict:
    """최종 목록에만 붙이는 중첩 정보 (중개사, 참고 실거래). 후보 전체에 만들면 낭비라 마지막에 한다."""
    record["broker"] = {
        "name": listing["broker_name"], "representative": listing["broker_representative"],
        "reg_no": listing["broker_reg_no"], "phone": listing["broker_phone"],
        "address": listing["broker_address"], "comment": listing["broker_comment"],
    }
    record["reference_transaction"] = (
        {
            "contract_date": listing["ref_contract_date"], "contract_type": listing["ref_contract_type"],
            "contract_term": listing["ref_contract_term"], "use_rr_right": listing["ref_use_rr_right"],
            "pre_deposit": listing["ref_pre_deposit"], "pre_monthly_rent": listing["ref_pre_monthly_rent"],
            "deposit": listing["ref_deposit"], "monthly_rent": listing["ref_monthly_rent"],
            "lease_kind": listing["ref_lease_kind"], "area": listing["ref_area"],
            "floor": listing["ref_floor"], "jibun": listing["ref_jibun"],
        }
        if listing["ref_contract_date"] else None
    )
    return record


def run_listing_diagnosis(request) -> dict:
    started = time.time()
    regions, _ = calculator._load_regions()
    work_lat, work_lon = _resolve_work_coords(request)

    effective_monthly_income = request.effective_monthly_income
    affordable_rent = calculator.calculate_affordable_rent(effective_monthly_income)
    rir = round((affordable_rent / effective_monthly_income) * 100, 1) if effective_monthly_income else 0.0

    # 보증금 한도: 희망 보증금/전세액이 있으면 그 금액, 없으면 현재 보유 보증금. 이를 넘는 매물은 추천하지 않는다.
    deposit_limit = request.desired_deposit if request.desired_deposit is not None else request.deposit

    # 1차: 자치구 대표좌표 기준 통근권 (이동수단별 버퍼로 넉넉하게)
    with ThreadPoolExecutor(max_workers=16) as pool:
        commute_results = list(pool.map(
            lambda r: calculator._commute_minutes(work_lat, work_lon, r.lat, r.lon, request.transport_type), regions
        ))
    for region, (minutes, source) in zip(regions, commute_results):
        if source == calculator.ESTIMATE_SOURCE_LABEL:
            logger.warning(f"카카오 API 사용 불가로 비상용 직선거리 추정 사용: {region.name} ({minutes}분 추정)")
    prefilter_buffer = calculator.REGION_PREFILTER_BUFFER_MIN_BY_MODE.get(
        request.transport_type, calculator.DEFAULT_REGION_PREFILTER_BUFFER_MIN
    )
    eligible = [
        (region, minutes, source)
        for region, (minutes, source) in zip(regions, commute_results)
        if minutes <= request.max_commute_minutes + prefilter_buffer
    ]

    all_types = {"아파트", "오피스텔", "연립다세대", "단독다가구"}
    selected_types = set(request.preferred_building_types) & all_types
    type_filter = selected_types if selected_types and selected_types != all_types else None
    today = date.today()

    wolse_results, jeonse_results = [], []
    listing_by_id: dict[str, dict] = {}
    total_candidates = 0

    for region, region_commute, commute_source in eligible:
        listings = listing_repository.load_district(region.name)
        if not listings:
            continue
        transportation_cost = calculator._transportation_cost(region_commute)
        matched_policies = policy_matcher.match_display_policies(request, building_region=region.name)  # 구별 1회

        for listing in listings:
            if listing["listing_status"] != listing_schema.LISTING_STATUS_AVAILABLE:
                continue  # 계약중 매물은 추천하지 않는다
            if type_filter and listing["property_type"] not in type_filter:
                continue
            if listing["lat"] is None or listing["lon"] is None:
                continue
            if (listing["deposit"] or 0) > deposit_limit:
                continue  # 보증금이 한도를 넘는 매물은 대출로 메운다고 가정하지 않고 제외
            if request.desired_rent is not None and (listing["monthly_rent"] or 0) > request.desired_rent:
                continue
            if not _move_in_ok(listing, request.move_schedule, today):
                continue

            total_candidates += 1
            result = _to_result(listing, region_commute, commute_source, transportation_cost, matched_policies)
            listing_by_id[listing["listing_id"]] = listing
            (wolse_results if listing["lease_type"] == "월세" else jeonse_results).append(result)

    # 월세/전세 각각 실질 주거비 낮은 순 상위 N (절감 기준이 없으니 require_savings=False)
    wolse = data_analysis.rank_by_real_cost(wolse_results, top_n=TOP_N, require_savings=False)
    jeonse = data_analysis.rank_by_real_cost(jeonse_results, top_n=TOP_N, require_savings=False)

    # 최종 목록만 매물 자신의 좌표로 통근시간을 정확히 다시 계산 (CSV에 좌표가 있으므로 지오코딩은 없다)
    def _refine(recommendations: list[dict]) -> list[dict]:
        recommendations = [_clean_nan(r) for r in recommendations]
        kept = calculator._recompute_precise_commute(
            recommendations, work_lat, work_lon, request.transport_type, request.max_commute_minutes
        )
        for r in kept:  # 통근시간이 정확해졌으니 교통비와 실질 주거비도 맞춰 갱신하고 다시 정렬
            r["transportation_cost"] = calculator._transportation_cost(r["commute_minutes"])
            r["real_housing_cost"] = (
                r["rent"] + r["maintenance_fee"] + r["loan_interest"] + r["transportation_cost"]
            )
            r["baseline_cost"] = r["real_housing_cost"]
            _attach_details(r, listing_by_id[r["listing_id"]])
        kept.sort(key=lambda r: (r["real_housing_cost"], r["listing_id"]))
        return kept

    wolse = _refine(wolse)
    jeonse = _refine(jeonse)

    used_distance_estimate = any(
        r["commute_source"] == calculator.ESTIMATE_SOURCE_LABEL for r in wolse + jeonse
    )
    logger.info(
        f"더미 매물 진단: 후보 {total_candidates}건 -> 월세 {len(wolse)} / 전세 {len(jeonse)} ({time.time() - started:.1f}초)"
    )
    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "wolse_recommendations": wolse,
        "jeonse_recommendations": jeonse,
        "used_distance_estimate": used_distance_estimate,
        "total_candidates": total_candidates,
        "deposit_limit": deposit_limit,
    }
