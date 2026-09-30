"""
[담당: 송귀성] 더미 매물 기반 추천 (POST /api/v1/diagnosis/listings)

기존 calculator.run_diagnosis(국토부 실거래가를 추천 매물로 사용)는 그대로 두고, 같은 입력으로
docs/samples/dummyhouses/*.csv 의 더미 매물을 추천한다. 국토부 실거래가는 추천 대상이 아니라 매물마다
붙는 "참고 실거래"(reference_transaction)로만 내려준다.

처리 흐름 (통근시간/정책/순위는 calculator·policy_matcher·data_analysis를 그대로 재사용):
1. 직장 좌표, 소득 기준 적정 월세 (수도권 RIR = 관리자 수정 > 기준소득관리에 저장된 값 우선, 없으면 docs/RIR.csv, 그것도 없으면 기본값 20%)
2. 자치구 대표좌표 기준 1차 통근권 필터 (이동수단별 버퍼, REGION_PREFILTER_BUFFER_MIN_BY_MODE)
3. 통근권 자치구의 CSV 매물 -> 계약가능만, 선호 유형, 보증금 한도 이하, 희망 월세 이하, 이사희망시기 필터.
   보증금 한도 = 희망 보증금(전세액)이 있으면 그 값, 없으면 현재 보유 보증금. 한도를 넘는 매물은 추천하지 않는다
   (2026-09-28: 예전엔 부족한 보증금을 대출로 채운다고 가정해 이자를 비용에 더했는데, 보증금이 부족한 매물을
   추천하는 게 이상하다는 지적으로 대출이자 계산을 없애고 한도 필터로 바꿨다)
4. 비용 계산 (2026-09-28: 교통비는 비용에서 아예 뺐다 - 통근시간은 희망 통근시간 필터와 표시에만 쓴다):
   - 실질 주거비 = 월세 + 관리비 + 대출이자                         (월세는 CSV 월세 그대로, 관리비는 그 매물의 관리비.
     2026-09-29: 전세는 월세가 없어 관리비만 남으면 실제 부담이 안 보여서, 매물 보증금 중 지금 가진 보증금으로
     못 채우는 부족분에 이자를 적용해 더한다. 금리는 아래 "보증금 기회비용"과 같은 값(전환율 API)을 그대로 쓴다
     (당초 임시 3% 고정값을 썼다가, 같은 API 값으로 통일했다 - 원금(부족분 vs 보증금 전체)만 다르다).
     월세는 대출이자 0 - 월세 자체가 이미 실제 부담을 보여준다)
   - 보증금 기회비용(월) = 보증금 x 연 전환율% / 12                 (보증금 전체를 월 비용으로 환산한 값 - 위 대출이자와 원금만 다른 같은 금리)
   - 보증금액 전환 이자기회비용 = 월세 + 관리비 + 보증금 기회비용   (보증금 크기까지 월 비용으로 환산해 비교)
   전환율은 한국부동산원(R-ONE) 수도권 전월세 전환율(종합주택)의 가장 최근 월 값이다 (reb_conversion_rate.py,
   2026-09-28: 예전 고정값 연 4.5%에서 변경. 이 앱은 수도권만 다루므로 수도권 값 하나만 쓴다)
   (교통비 항목은 응답 호환을 위해 남기되 0, 응답에서는 내보내지 않는다)
5. 월세/전세 따로 **보증금전환 실질거주비** 낮은 순으로 상위 TOP_N (지역별 비례 배분) - 순위 기준은 2026-09-28부터
   실질 주거비가 아니라 보증금전환 실질거주비다 (보증금이 큰 반전세가 월세만 낮다는 이유로 상위를 차지하지 않게)
6. 최종 목록만 매물 자신의 좌표로 통근시간을 정확히 다시 계산(CSV에 좌표가 이미 있어 주소/좌표 외부 API
   호출은 없다)해서 희망 통근시간을 넘는 매물을 제외한다
"""
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date

from app.services import (
    calculator, data_analysis, listing_repository, listing_schema, loan_matcher, policy_matcher, reb_conversion_rate, rir_stats,
)

logger = logging.getLogger(__name__)

TOP_N = 1000  # 월세/전세 각각 최대 추천 수 (지도 마커/카드 렌더링이 버틸 수 있는 상한)

# 추천 순위를 매기는 비용 항목: 보증금전환 실질거주비 (월세 + 관리비 + 보증금 기회비용)
RANK_COST_KEY = "deposit_converted_cost"


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


def _resolve_rir(request, effective_monthly_income: float) -> tuple[float, float, dict]:
    """수도권 RIR(%)·적정 월세 상한·리포트용 rir_fields(연도/출처/전국/소득수준별)를 계산한다.
    우선순위: 1) 관리자 수정 > 기준소득관리에 저장된 값(request.income_standard) 2) docs/RIR.csv(rir_stats, 레거시 폴백)
    3) 둘 다 없으면 DEFAULT_RIR_PERCENT(20%, 전국/소득수준별 표시 없이 헤드라인만)."""
    income_standard = getattr(request, "income_standard", None)
    if income_standard and income_standard.rir_metro_percent is not None:
        rir = income_standard.rir_metro_percent
        affordable_rent = round(effective_monthly_income * rir / 100, 1)
        by_income = [
            (key, label, percent)
            for key, label, percent in (
                ("low", "하위(1-4분위)", income_standard.rir_low_percent),
                ("mid", "중위(5-8분위)", income_standard.rir_mid_percent),
                ("high", "상위(9-10분위)", income_standard.rir_high_percent),
            )
            if percent is not None
        ]
        overall = income_standard.rir_overall_percent
        rir_fields = {
            "rir_year": income_standard.rir_year,
            "rir_source": income_standard.rir_source or "",
            "rir_monthly_income": round(effective_monthly_income, 1),
            "rir_metro_affordable_rent": affordable_rent,
            "rir_overall_percent": overall,
            "rir_overall_affordable_rent": round(effective_monthly_income * overall / 100, 1) if overall is not None else None,
            "rir_by_income": [
                {"key": key, "label": label, "rir_percent": percent, "affordable_rent": round(effective_monthly_income * percent / 100, 1)}
                for key, label, percent in by_income
            ],
        }
        return rir, affordable_rent, rir_fields

    rir_data = rir_stats.get_rir_stats()
    if rir_data:
        rir = rir_data.metro_percent
        affordable_rent = round(effective_monthly_income * rir / 100, 1)
        overall = rir_data.overall_percent
        rir_fields = {
            "rir_year": rir_data.year,
            "rir_source": rir_data.source,
            "rir_monthly_income": round(effective_monthly_income, 1),
            "rir_metro_affordable_rent": affordable_rent,
            "rir_overall_percent": overall,
            "rir_overall_affordable_rent": round(effective_monthly_income * overall / 100, 1) if overall is not None else None,
            "rir_by_income": [
                {"key": lv.key, "label": lv.label, "rir_percent": lv.rir_percent,
                 "affordable_rent": round(effective_monthly_income * lv.rir_percent / 100, 1)}
                for lv in rir_data.income_levels
            ],
        }
        return rir, affordable_rent, rir_fields

    rir = rir_stats.DEFAULT_RIR_PERCENT
    return rir, round(effective_monthly_income * rir / 100, 1), {}


def _rank_cost(listing: dict, deposit_rate_percent: float) -> float:
    """_to_result()의 deposit_converted_cost(순위 기준값, RANK_COST_KEY)만 떼어낸 가벼운 버전.
    후보 전체(자치구 통근권 안 매물 전부, 많으면 수만 건)에 매번 _to_result()의 나머지 20여 개
    필드(브로커, 참고 실거래, 통근시간 재계산용 좌표 등)까지 만들면 순위에서 떨어질 후보에도
    똑같이 메모리를 쓰게 된다(2026-09-30: Render 무료 인스턴스 512MB 한도로 실제 OOM 발생 확인).
    juso_api/kakao_geocode의 "최종 목록에만" 원칙과 같은 이유로, 순위를 매기는 이 값만 먼저
    계산해 두고 _to_result()는 랭킹이 끝나 top_n으로 추려진 뒤에만 부른다(run_listing_diagnosis
    참고)."""
    rent = listing["monthly_rent"] or 0
    deposit = listing["deposit"] or 0
    maintenance_fee = listing["maintenance_fee"] or 0
    deposit_opportunity_cost = round(deposit * deposit_rate_percent / 100 / 12, 1)
    return round(rent + maintenance_fee + deposit_opportunity_cost, 1)


def _to_result(listing: dict, region_commute: int, commute_source: str, deposit_rate_percent: float, current_deposit: int) -> dict:
    rent = listing["monthly_rent"] or 0
    deposit = listing["deposit"] or 0
    maintenance_fee = listing["maintenance_fee"] or 0
    # 전세는 월세가 없어서 "실질 주거비"가 관리비만 남아 실제 부담을 못 보여줬다 (2026-09-29). 매물 보증금 중
    # 지금 가진 보증금(current_deposit)으로 못 채우는 부족분만 대출로 메운다고 보고, 그 금액에 이자를 적용해
    # 관리비에 더한다. 금리는 "보증금액 전환 이자기회비용"(deposit_opportunity_cost)과 같은 값(deposit_rate_percent -
    # 한국부동산원 R-ONE 수도권 전월세 전환율 API)을 그대로 재사용한다(2026-09-29: 임시 3% 고정값에서 변경).
    # 월세는 월세 자체가 이미 실제 부담을 보여주므로 그대로 0 (기존과 동일).
    if listing["lease_type"] == "전세":
        deposit_shortfall = max(0, deposit - current_deposit)  # 부족분(대출금액): 매물 보증금 - 현재 보증금
        # real_housing_cost/loan_interest는 응답 모델(response_schema.BuildingRecommendation)에서 int라
        # 기존 calculator.py의 대출이자 계산과 같이 정수(만원)로 반올림한다 (소수 자리는 deposit_opportunity_cost 등 float 필드가 담당).
        loan_interest = round(deposit_shortfall * deposit_rate_percent / 100 / 12)
    else:
        deposit_shortfall = 0
        loan_interest = 0
    real_housing_cost = rent + maintenance_fee + loan_interest  # 교통비는 뺐다
    # 보증금 기회비용(월) = 보증금 x 연 전환율% / 12 (한국부동산원 수도권 전월세 전환율), 보증금전환 실질거주비 = 월세 + 관리비 + 보증금 기회비용 (만원, 소수 첫째 자리)
    deposit_opportunity_cost = round(deposit * deposit_rate_percent / 100 / 12, 1)
    deposit_converted_cost = round(rent + maintenance_fee + deposit_opportunity_cost, 1)
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
        "deposit_shortfall": deposit_shortfall,  # 부족분(대출금액) = 매물 보증금 - 현재 보증금 (전세만, 월세는 0)
        "government_support": 0,
        "real_housing_cost": real_housing_cost,
        "deposit_opportunity_cost": deposit_opportunity_cost,
        "deposit_converted_cost": deposit_converted_cost,
        "baseline_cost": real_housing_cost,  # 비교할 별도 기준이 없다 (월세/전세 모두 절감액 0)
        "monthly_savings": 0,
        "matched_policies": [],  # 정책은 응답 최상위 policies_by_region에 자치구별로 한 번만 담는다 (매물마다 반복하지 않는다)
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
    # 소득 대비 주택임대료 비율(RIR): 관리자 수정 > 기준소득관리에 저장된 값이 우선이고, 없으면 docs/RIR.csv(레거시 폴백),
    # 그것도 없으면 소득의 20%(DEFAULT_RIR_PERCENT) 기본값이다 - _resolve_rir 참고.
    rir, affordable_rent, rir_fields = _resolve_rir(request, effective_monthly_income)

    # 보증금을 월 비용으로 환산하는 이율: 한국부동산원 수도권 전월세 전환율(종합주택) 최신 월 값 (12시간 캐시, 조회 실패 시 대체값)
    deposit_rate = reb_conversion_rate.get_metro_conversion_rate()

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
    policies_by_region: dict[str, list[dict]] = {}  # 자치구 -> 그 자치구에 매칭된 정책 (서울 공통 + 그 자치구)
    listing_by_id: dict[str, dict] = {}
    total_candidates = 0

    for region, region_commute, commute_source in eligible:
        listings = listing_repository.load_district(region.name)
        if not listings:
            continue
        policies_by_region[region.name] = policy_matcher.match_display_policies(request, building_region=region.name)  # 구별 1회

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
            listing_id = listing["listing_id"]
            listing_by_id[listing_id] = listing
            # 순위 산출에 필요한 값만 담은 가벼운 레코드 (전체 필드는 top_n으로 추려진 뒤 _expand에서 채운다).
            lightweight = {
                "listing_id": listing_id,
                "region": listing["region"],
                RANK_COST_KEY: _rank_cost(listing, deposit_rate.rate_percent),
                "monthly_savings": 0,  # rank_by_real_cost의 동점자 2차 기준. 여기선 항상 0(_to_result와 동일).
                "commute_minutes": region_commute,
                "commute_source": commute_source,
            }
            (wolse_results if listing["lease_type"] == "월세" else jeonse_results).append(lightweight)

    # 월세/전세 각각 보증금전환 실질거주비 낮은 순 상위 N (절감 기준이 없으니 require_savings=False)
    wolse = data_analysis.rank_by_real_cost(wolse_results, top_n=TOP_N, require_savings=False, cost_key=RANK_COST_KEY)
    jeonse = data_analysis.rank_by_real_cost(jeonse_results, top_n=TOP_N, require_savings=False, cost_key=RANK_COST_KEY)

    # 랭킹이 끝나 top_n(최대 TOP_N x 2)으로 추려진 뒤에야 _to_result()로 나머지 필드(브로커/좌표 등)를 채운다.
    def _expand(ranked: list[dict]) -> list[dict]:
        return [
            _to_result(listing_by_id[r["listing_id"]], r["commute_minutes"], r["commute_source"],
                       deposit_rate.rate_percent, request.deposit)
            for r in ranked
        ]

    wolse = _expand(wolse)
    jeonse = _expand(jeonse)

    # 최종 목록만 매물 자신의 좌표로 통근시간을 정확히 다시 계산 (CSV에 좌표가 있으므로 지오코딩은 없다)
    def _refine(recommendations: list[dict]) -> list[dict]:
        recommendations = [_clean_nan(r) for r in recommendations]
        kept = calculator._recompute_precise_commute(
            recommendations, work_lat, work_lon, request.transport_type, request.max_commute_minutes
        )
        for r in kept:  # 교통비가 없으니 통근시간이 정확해져도 비용은 그대로다 - 매물 상세 정보만 붙인다
            _attach_details(r, listing_by_id[r["listing_id"]])
            # 관리자 화면에서 저장한 대출 조건으로 이 매물에 신청 가능한 대출을 판별한다 (저장된 대출이 없으면 빈 목록).
            # 대출금리표가 없는 대출의 기본금리는 이 매물 계산에 쓴 것과 같은 기준금리 API 값(deposit_rate)을 그대로 쓴다.
            r["eligible_loans"] = loan_matcher.match_eligible_loans(request, r, deposit_rate.rate_percent)
        kept.sort(key=lambda r: (r[RANK_COST_KEY], r["listing_id"]))
        return kept

    wolse = _refine(wolse)
    jeonse = _refine(jeonse)

    # 주거정책 추천: 직장 위치 자치구(work_region) 정책을 기본으로 보여주고, 매물을 클릭하면 그 매물 자치구 정책으로 바꾼다.
    # 지역값이 "서울"인 정책은 어느 자치구든(25개 모두) match_display_policies가 함께 돌려준다. 추천 결과에 나온 자치구와
    # 직장 자치구의 정책만 내려준다 (직장 자치구가 통근권 목록에 없어도 기본 표시가 비지 않게 따로 계산).
    work_region = request.work_location
    if work_region not in policies_by_region:
        policies_by_region[work_region] = policy_matcher.match_display_policies(request, building_region=work_region)
    wanted = {r["region"] for r in wolse + jeonse} | {work_region}
    policies_by_region = {name: pols for name, pols in policies_by_region.items() if name in wanted}

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
        "work_region": work_region,
        "policies_by_region": policies_by_region,
        "deposit_conversion_rate": deposit_rate.rate_percent,
        "deposit_conversion_rate_base": deposit_rate.base_month,
        "deposit_conversion_rate_label": deposit_rate.label,
        "deposit_conversion_rate_is_fallback": deposit_rate.is_fallback,
        **rir_fields,
    }
