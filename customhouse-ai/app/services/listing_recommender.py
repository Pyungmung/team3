"""
[담당: 송귀성] 더미 매물 기반 추천 (POST /api/v1/diagnosis/listings)

docs/samples/dummyhouses/*.csv 의 더미 매물을 추천한다. 국토부 실거래가는 추천 대상이 아니라, 카드를
펼치면 그때 실시간으로 조회하는 "참고 실거래"용이다(app/api/v1/listing_reference.py 참고).

처리 흐름 (정책/순위는 calculator·policy_matcher·data_analysis를 그대로 재사용):
1. 직장 좌표, 소득 기준 적정 월세 (수도권 RIR = 관리자 수정 > 기준소득관리에 저장된 값 우선, 없으면 docs/RIR.csv, 그것도 없으면 기본값 20%)
2. 자치구 대표좌표 기준 1차 통근권 필터 (이동수단별 최대 거리 + 버퍼, calculator.REGION_PREFILTER_BUFFER_KM) - 어느 자치구
   CSV를 아예 읽을지만 정하는 용도(IO 절약)이고, 매물 하나하나를 거르는 기준은 아니다.
3. 통근권 자치구의 CSV 매물 -> 계약가능만, 선호 유형, 보증금 한도 이하, 희망 월세 이하, 이사희망시기,
   그리고 "이 매물 자신의" 좌표 기준 직선거리 추정 통근시간 필터 - 자치구가 넓으면(예: 서초구) 대표좌표
   하나로만 거를 경우, 직장에서 먼 동네의 싼 매물이 5단계 지역별 비례 배분 몫을 먼저 차지해 정작
   직장 바로 옆 매물이 순위 경쟁에도 못 들어가는 문제가 있었다(2026-10-01).
   보증금 한도 = 희망 보증금(전세액)이 있으면 그 값, 없으면 현재 보유 보증금. 한도를 넘는 매물은 추천하지 않는다
   (2026-09-28: 예전엔 부족한 보증금을 대출로 채운다고 가정해 이자를 비용에 더했는데, 보증금이 부족한 매물을
   추천하는 게 이상하다는 지적으로 대출이자 계산을 없애고 한도 필터로 바꿨다)
4. 비용 계산 (2026-09-28: 교통비는 비용에서 아예 뺐다 - 통근시간은 희망 통근시간 필터와 표시에만 쓴다):
   - 실질 주거비 = 월세 + 관리비 + 대출이자                         (월세는 CSV 월세 그대로, 관리비는 그 매물의 관리비.
     2026-09-29: 전세는 월세가 없어 관리비만 남으면 실제 부담이 안 보여서, 매물 보증금 중 지금 가진 보증금으로
     못 채우는 부족분에 이자를 적용해 더한다. 금리는 아래 "보증금 기회비용"과 같은 값(전환율 API)을 그대로 쓴다
     (당초 임시 3% 고정값을 썼다가, 같은 API 값으로 통일했다 - 원금(부족분 vs 보증금 전체)만 다르다).
     월세는 대출이자 0 - 월세 자체가 이미 실제 부담을 보여준다)
   - 보증금 기회비용(월) = 보증금 x 연 전환율% / 12                 (보증금 전체를 월 비용으로 환산한 값 - 위 대출이자와 원금만 다른 같은 금리.
     2026-10-03: 월세 매물만 전환율 대신 예금은행 정기예금(1년) 금리를 쓴다 - "예금 전환 이자기회비용", deposit_interest_rate.py)
   - 보증금액 전환 이자기회비용 = 월세 + 관리비 + 보증금 기회비용   (보증금 크기까지 월 비용으로 환산해 비교)
   전환율은 한국부동산원(R-ONE) 수도권 전월세 전환율(종합주택)의 가장 최근 월 값이다 (reb_conversion_rate.py,
   2026-09-28: 예전 고정값 연 4.5%에서 변경. 이 앱은 수도권만 다루므로 수도권 값 하나만 쓴다)
   (교통비 항목은 응답 호환을 위해 남기되 0, 응답에서는 내보내지 않는다)
5. 월세/전세 따로 **보증금전환 실질거주비** 낮은 순으로 상위 N(기본 DEFAULT_TOP_N, 관리자 기타 설정으로 변경) (지역별 비례 배분) - 순위 기준은 2026-09-28부터
   실질 주거비가 아니라 보증금전환 실질거주비다 (보증금이 큰 반전세가 월세만 낮다는 이유로 상위를 차지하지 않게)

통근시간 표시(2026-10-01, 검색/매칭을 가볍게 하는 쪽으로 전환): 위 1~5단계(검색·필터·랭킹) 전체가
카카오 API 호출 없이 직선거리 추정(calculator._estimate_commute_minutes_fallback)만으로 돌아간다 -
후보가 많으면 수만 건이라 매번 카카오 API를 부르면 느리고(예전엔 최종 목록 전체를 다시 불렀다) 호출
한도도 쉽게 소진된다. 이 응답의 commute_minutes/commute_source는 전부 그 추정치이고, 카카오 API 기준
정확한 통근시간은 화면에 "보이는" 매물 카드에 대해서만 GET /api/v1/listings/{listing_id}/commute
(app/api/v1/listing_commute.py)로 그때그때 따로 조회해 표시를 갱신한다 - listing_reference.py의
"실거래 참고"와 같은 패턴.
"""
import logging
import random
import time
from datetime import date

from app.services import (
    calculator, data_analysis, deposit_interest_rate, listing_repository, listing_schema, loan_matcher, policy_matcher,
    reb_conversion_rate, rir_stats,
)

logger = logging.getLogger(__name__)

MAX_AD_CARDS = 100  # 월세/전세 각각 응답에 담는 광고 매물 최대 수 (2026-10-08)
DEFAULT_TOP_N = 500  # 월세/전세 각각 최대 추천 수 기본값 (2026-10-05: 1000 -> 500 - 응답 4MB/연속 요청 시 Render 무료 인스턴스 헬스체크 타임아웃 완화)
MAX_TOP_N = 1000  # 관리자 설정으로도 넘을 수 없는 상한 (지도 마커/카드 렌더링이 버틸 수 있는 한계)
MIN_TOP_N = 10


def _top_n(request) -> int:
    """월세/전세 각각의 추천 개수 상한. 관리자 수정 > 기타 설정의 값(request.app_settings.recommendation_limit)이 있으면
    그 값(MIN_TOP_N~MAX_TOP_N로 보정), 없으면 DEFAULT_TOP_N."""
    settings = getattr(request, "app_settings", None)
    limit = getattr(settings, "recommendation_limit", None) if settings else None
    if limit is None:
        return DEFAULT_TOP_N
    return max(MIN_TOP_N, min(int(limit), MAX_TOP_N))

# 추천 순위를 매기는 비용 항목: 보증금전환 실질거주비 (월세 + 관리비 + 보증금 기회비용)
RANK_COST_KEY = "deposit_converted_cost"


# 희망 이사 일정 -> 이사가능일이 오늘로부터 며칠 이내여야 하는지. 그 외(WITHIN_6M/EXPLORING/미지정)는 제한 없음.
MOVE_SCHEDULE_MAX_DAYS = {"IMMEDIATE": 30, "WITHIN_3M": 90}


def _resolve_work_coords(request) -> tuple[float, float]:
    """정확한 좌표(work_lat/lon)가 오면 그대로, 아니면 이름으로 조회."""
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


def _opportunity_rate_percent(listing: dict, deposit_rate_percent: float, monthly_deposit_rate_percent: float | None) -> float:
    """보증금 기회비용(예금 전환 이자기회비용)에 쓰는 이율. 월세 매물은 "그 보증금을 예금에 넣었다면 받았을 이자"라서
    예금은행 정기예금(1년) 금리(monthly_deposit_rate_percent)를 쓰고(2026-10-03), 전세(와 값을 안 넘긴 호출)는
    기존대로 수도권 전월세 전환율(deposit_rate_percent)을 쓴다. 전세의 부족분 대출이자/대출 기본금리는 이 함수와
    무관하게 항상 전환율이다."""
    if listing["lease_type"] == "월세" and monthly_deposit_rate_percent is not None:
        return monthly_deposit_rate_percent
    return deposit_rate_percent


def _rank_cost(listing: dict, deposit_rate_percent: float, monthly_deposit_rate_percent: float | None = None) -> float:
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
    opportunity_rate = _opportunity_rate_percent(listing, deposit_rate_percent, monthly_deposit_rate_percent)
    deposit_opportunity_cost = round(deposit * opportunity_rate / 100 / 12, 1)
    return round(rent + maintenance_fee + deposit_opportunity_cost, 1)


def _to_result(listing: dict, region_commute: int, commute_source: str, deposit_rate_percent: float, current_deposit: int,
               monthly_deposit_rate_percent: float | None = None) -> dict:
    rent = listing["monthly_rent"] or 0
    deposit = listing["deposit"] or 0
    maintenance_fee = listing["maintenance_fee"] or 0
    # 전세는 월세가 없어서 "실질 주거비"가 관리비만 남아 실제 부담을 못 보여줬다 (2026-09-29). 매물 보증금 중
    # 지금 가진 보증금(current_deposit)으로 못 채우는 부족분만 대출로 메운다고 보고, 그 금액에 이자를 적용해
    # 관리비에 더한다. 금리는 "보증금액 전환 이자기회비용"(deposit_opportunity_cost)과 같은 값(deposit_rate_percent -
    # 한국부동산원 R-ONE 수도권 전월세 전환율 API)을 그대로 재사용한다(2026-09-29: 임시 3% 고정값에서 변경).
    # 2026-10-06: 월세도 같은 방식이다 - 매물 보증금이 지금 가진 보증금을 넘으면 그 부족분에 같은 기준이자(전환율)를 적용한
    # 월 이자를 월세 + 관리비에 더한다 (보증금이 이하면 부족분 0, 이자 0으로 기존과 동일). 순위(보증금 전환 이자기회비용)에는 영향 없다.
    deposit_shortfall = max(0, deposit - current_deposit)  # 부족분(대출금액): 매물 보증금 - 현재 보증금
    # real_housing_cost/loan_interest는 응답 모델(response_schema.BuildingRecommendation)에서 int라
    # 기존 calculator.py의 대출이자 계산과 같이 정수(만원)로 반올림한다 (소수 자리는 deposit_opportunity_cost 등 float 필드가 담당).
    loan_interest = round(deposit_shortfall * deposit_rate_percent / 100 / 12)
    real_housing_cost = rent + maintenance_fee + loan_interest  # 교통비는 뺐다
    # 보증금 기회비용(월) = 보증금 x 연 전환율% / 12 (한국부동산원 수도권 전월세 전환율), 보증금전환 실질거주비 = 월세 + 관리비 + 보증금 기회비용 (만원, 소수 첫째 자리)
    # 2026-10-03: 월세 매물은 전환율이 아니라 예금은행 정기예금(1년) 금리로 환산한다(_opportunity_rate_percent).
    opportunity_rate = _opportunity_rate_percent(listing, deposit_rate_percent, monthly_deposit_rate_percent)
    deposit_opportunity_cost = round(deposit * opportunity_rate / 100 / 12, 1)
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
        "deposit_shortfall": deposit_shortfall,  # 부족분(대출금액) = 매물 보증금 - 현재 보증금 (전세/월세 공통)
        "government_support": 0,
        "real_housing_cost": real_housing_cost,
        "deposit_opportunity_cost": deposit_opportunity_cost,
        "deposit_opportunity_rate_percent": opportunity_rate,  # 이 매물의 예금 전환 이자기회비용에 실제로 쓴 연 %
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
        "jeonse_loan_available": bool(listing.get("jeonse_loan_available", True)),
        "description": listing["description"],
        "road_address": listing["road_address"],
        "jibun_address": listing["jibun_address"],
        "postal_code": listing["postal_code"],
        "address_source": listing["address_source"],
    }


def _attach_details(record: dict, listing: dict) -> dict:
    """최종 목록에만 붙이는 중첩 정보 (중개사). 후보 전체에 만들면 낭비라 마지막에 한다.
    실거래 참고(reference_transaction)는 더 이상 CSV의 고정값을 쓰지 않는다 - 화면에서 카드를
    펼칠 때 GET /api/v1/listings/{listing_id}/reference로 그때그때 실시간 조회한다
    (reference_lookup.py 참고)."""
    record["broker"] = {
        "name": listing["broker_name"], "representative": listing["broker_representative"],
        "reg_no": listing["broker_reg_no"], "phone": listing["broker_phone"],
        "address": listing["broker_address"], "comment": listing["broker_comment"],
    }
    return record


def _build_ad_cards(request, ad_listing_ids: list[str], taken_ids: set[str], work_lat: float, work_lon: float,
                    deposit_limit: int, deposit_rate, monthly_deposit_rate) -> tuple[list[dict], list[dict]]:
    """광고하기 매물 카드 (2026-10-08): 광고 중인 매물 중 이 사용자에게 보여줄 수 있는 것만 월세/전세로 나눠 무작위 순서로 돌려준다.
    조건 = 직장 자치구에 있고, 이 매물 자신의 직선거리 추정 통근시간이 희망 통근시간 이내이며, 보증금이 한도 이하이고, 계약가능 상태.
    추천 순위/유형/월세 필터와는 무관하다(광고는 추천 로직과 상관없이 끼워 넣는다). 이미 일반 추천에 나온 매물(taken_ids)은 빼서
    같은 매물이 두 번 보이지 않게 한다. 광고 매물 정보는 복사해 두지 않고 매번 원본 매물에서 읽으므로 원본을 고치면 그대로 반영된다."""
    wolse_ads: list[dict] = []
    jeonse_ads: list[dict] = []
    for listing_id in dict.fromkeys(ad_listing_ids):  # 중복 제거(순서 유지)
        if listing_id in taken_ids:
            continue
        listing = listing_repository.get_listing(listing_id)
        if listing is None or listing["listing_status"] != listing_schema.LISTING_STATUS_AVAILABLE:
            continue
        if listing["region"] != request.work_location:
            continue
        if listing["lat"] is None or listing["lon"] is None:
            continue
        if (listing["deposit"] or 0) > deposit_limit:
            continue
        minutes = calculator._estimate_commute_minutes_fallback(work_lat, work_lon, listing["lat"], listing["lon"], request.transport_type)
        if minutes > request.max_commute_minutes:
            continue
        card = _clean_nan(_to_result(listing, minutes, calculator.ESTIMATE_SOURCE_LABEL, deposit_rate.rate_percent, request.deposit,
                                     monthly_deposit_rate.rate_percent))
        _attach_details(card, listing)
        card["eligible_loans"] = loan_matcher.match_eligible_loans(request, card, deposit_rate.rate_percent)
        card["is_ad"] = True
        (wolse_ads if listing["lease_type"] == "월세" else jeonse_ads).append(card)
    random.shuffle(wolse_ads)
    random.shuffle(jeonse_ads)
    return wolse_ads[:MAX_AD_CARDS], jeonse_ads[:MAX_AD_CARDS]


def refresh_listing_card(request, listing_id: str) -> dict | None:
    """관심매물 새로고침 - 매물번호로 그 매물 1건의 추천 카드를 사용자의 현재 조건으로 다시 만든다 (2026-10-05).
    run_listing_diagnosis와 같은 계산(_to_result/_attach_details/대출 판별)을 쓰되, 순위/필터(보증금 한도, 통근시간,
    유형, 이사 일정, 계약중 여부)는 적용하지 않는다 - 이미 관심매물로 담은 매물의 가격/비용을 새 기준으로 갱신하는 용도라서
    조건에 안 맞게 되었어도 카드는 만들어 준다. 매물이 없으면(삭제됨) None."""
    listing = listing_repository.get_listing(listing_id)
    # 삭제는 행을 지우지 않고 상태만 "삭제됨"으로 바꾸므로 상태도 같이 본다 (2026-10-07)
    if listing is None or listing["listing_status"] == listing_schema.LISTING_STATUS_DELETED:
        return None
    work_lat, work_lon = _resolve_work_coords(request)
    deposit_rate = reb_conversion_rate.get_metro_conversion_rate()
    monthly_deposit_rate = deposit_interest_rate.get_one_year_deposit_rate()
    if listing["lat"] is not None and listing["lon"] is not None:
        minutes = calculator._estimate_commute_minutes_fallback(work_lat, work_lon, listing["lat"], listing["lon"], request.transport_type)
    else:
        minutes = 0
    card = _to_result(listing, minutes, calculator.ESTIMATE_SOURCE_LABEL, deposit_rate.rate_percent, request.deposit,
                      monthly_deposit_rate.rate_percent)
    card = _clean_nan(card)
    _attach_details(card, listing)
    card["eligible_loans"] = loan_matcher.match_eligible_loans(request, card, deposit_rate.rate_percent)
    return {
        "listing": card,
        "deposit_conversion_rate": deposit_rate.rate_percent,
        "deposit_conversion_rate_base": deposit_rate.base_month,
        "deposit_conversion_rate_label": deposit_rate.label,
        "deposit_conversion_rate_is_fallback": deposit_rate.is_fallback,
        "monthly_deposit_rate": monthly_deposit_rate.rate_percent,
        "monthly_deposit_rate_base": monthly_deposit_rate.base_month,
        "monthly_deposit_rate_label": monthly_deposit_rate.label,
        "monthly_deposit_rate_is_fallback": monthly_deposit_rate.is_fallback,
    }


HOME_PREVIEW_TOP_N = 200  # 홈 미리보기는 대충 보여주는 용도라 월세/전세 각각 상위 200건만 쓴다 (전체 진단은 DEFAULT_TOP_N=500)


def run_home_preview(request) -> dict:
    """메인 홈 미리보기용 요약 (2026-10-06): 적정 월세 상한/수도권 주거비 비율, 추천 지역(평균 통근시간이 짧은 자치구 3곳),
    예상 평균 통근시간(+25~75번째 백분위 범위). 전체 진단(run_listing_diagnosis)과 같은 계산이지만 보증금 필터는 "모두 표시"로 두고
    (대출 판별을 하지 않는다) 상위 HOME_PREVIEW_TOP_N건만 뽑는다 - 홈에서 로그인한 회원이 마이페이지 조건으로 바로 보는 대략적인 값이다."""
    from app.models.request_schema import AppSettingCondition
    light = request.model_copy(update={
        "show_all_deposits": True,
        "loan_products": [],
        "app_settings": AppSettingCondition(recommendationLimit=HOME_PREVIEW_TOP_N),
        "ad_listing_ids": [],  # 홈 요약 통계에는 광고 카드를 만들 필요가 없다
    })
    data = run_listing_diagnosis(light)
    listings = data["wolse_recommendations"] + data["jeonse_recommendations"]
    by_region: dict[str, list[int]] = {}
    for r in listings:
        if r.get("region") and isinstance(r.get("commute_minutes"), (int, float)):
            by_region.setdefault(r["region"], []).append(r["commute_minutes"])
    regions = [name for name, _ in sorted(
        ((n, sum(m) / len(m)) for n, m in by_region.items() if len(m) >= 3), key=lambda x: x[1])[:3]]
    minutes = sorted(r["commute_minutes"] for r in listings if isinstance(r.get("commute_minutes"), (int, float)))
    avg = lo = hi = None
    if minutes:
        avg = round(sum(minutes) / len(minutes))
        lo = minutes[int(0.25 * (len(minutes) - 1))]
        hi = minutes[int(0.75 * (len(minutes) - 1))]
    return {
        "affordable_rent": data["affordable_rent"],
        "rent_to_income_ratio": data["rent_to_income_ratio"],
        "regions": regions,
        "avg_commute_minutes": avg,
        "commute_range": (f"{lo}분" if lo == hi else f"{lo}~{hi}분") if avg is not None else None,
    }


def _loan_possible(request, listing: dict, cache: dict) -> bool:
    """이 매물에 신청 가능한 대출이 하나라도 있는가 (보증금 필터용, 2026-10-06). 순위/카드용 계산(match_eligible_loans)과 같은
    자격 판별(loan_matcher.is_eligible)을 쓰되 금리/이자 계산은 하지 않는다. 저장된 대출이 없거나 "정책 대출 활용"을 껐으면 False.
    등록자가 "전세자금대출 불가"로 표시한 매물은 전세/월세 구분 없이 False(보증부월세대출도 불가). 같은 (거래유형, 보증금, 월세, 면적)이면 결과를 재사용한다."""
    if not request.loan_products or not request.use_loan_policy:
        return False
    lease = listing["lease_type"]
    if listing.get("jeonse_loan_available") is False:
        return False
    # 결과가 면적/월세에 따라 달라지는 대출(전용면적/월세 제한이 있는 대출)이 하나도 없으면 그 값을 키에서 빼서 캐시 적중률을 높인다
    # (매물마다 면적이 달라 그대로 두면 후보 1만 건대에서 거의 매번 다시 계산한다).
    if "_uses" not in cache:
        cache["_uses"] = (
            any(l.max_exclusive_area is not None or any(p.override_max_exclusive_area is not None for p in l.preferences.values())
                for l in request.loan_products),
            any(l.max_listing_monthly_rent is not None for l in request.loan_products),
        )
    uses_area, uses_rent = cache["_uses"]
    key = (lease, listing["deposit"], listing["monthly_rent"] if uses_rent else None, listing["exclusive_area"] if uses_area else None)
    if key not in cache:
        probe = {"lease_type": lease, "listing_deposit": listing["deposit"] or 0,
                 "listing_monthly_rent": listing["monthly_rent"] or 0, "exclusive_area": listing["exclusive_area"]}
        cache[key] = any(loan_matcher.is_eligible(loan, request, probe) for loan in request.loan_products)
    return cache[key]


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
    # 월세 매물의 예금 전환 이자기회비용 전용 이율: 예금은행 정기예금(1년) 금리 (deposit_interest_rate.py, 2026-10-03)
    monthly_deposit_rate = deposit_interest_rate.get_one_year_deposit_rate()

    # 보증금 한도: 희망 보증금/전세액이 있으면 그 금액, 없으면 현재 보유 보증금. 이를 넘는 매물은 추천하지 않는다.
    deposit_limit = request.desired_deposit if request.desired_deposit is not None else request.deposit

    # 1차: 자치구 대표좌표 기준 통근권 - 어느 자치구 CSV를 읽을지만 정하는 IO 절약용이라 버퍼를 넉넉히 두고,
    # 카카오 API 호출 없이 직선거리 추정(계산만 하므로 25개 자치구 전부 즉시)으로 거른다(2026-10-01).
    # 2026-10-06: 이동수단별 보정식으로 환산한 최대 직선거리 + 자치구 대표좌표 오차를 감당하는 여유(km)
    prefilter_km = calculator.max_commute_distance_km(request.max_commute_minutes, request.transport_type)         + calculator.REGION_PREFILTER_BUFFER_KM
    eligible = [
        region for region in regions
        if calculator._haversine_km(work_lat, work_lon, region.lat, region.lon) <= prefilter_km
    ]

    all_types = {"아파트", "오피스텔", "연립다세대", "단독다가구"}
    selected_types = set(request.preferred_building_types) & all_types
    type_filter = selected_types if selected_types and selected_types != all_types else None
    today = date.today()

    wolse_results, jeonse_results = [], []
    # 보증금 필터 (2026-10-06): 기본은 "현재 보유 보증금 이하 + (초과해도 대출 가능한 매물)"만 추천한다. 리포트의 "모두 표시"를
    # 체크하면(show_all_deposits) 최대 매물 보증금(deposit_limit) 이하를 전부 추천한다.
    own_deposit = request.deposit or 0
    loan_cache: dict = {}
    excluded_no_loan = excluded_semi_jeonse = 0
    policies_by_region: dict[str, list[dict]] = {}  # 자치구 -> 그 자치구에 매칭된 정책 (서울 공통 + 그 자치구)
    listing_by_id: dict[str, dict] = {}
    total_candidates = 0

    for region in eligible:
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

            # 2026-10-01: 순위(가격) 단계도 자치구 대표좌표가 아니라 "이 매물 자신의" 직선거리
            # 추정치로 거른다 - 서초구처럼 넓은 자치구는 대표좌표 하나로만 거르면, 직장에서 먼
            # 동네의 싼 매물이 "자치구 몫"(rank_by_real_cost의 지역별 비례 배분)을 먼저 차지해버려서,
            # 정작 직장 바로 옆인데 가격이 살짝 더 비싼 매물이 순위 단계에도 못 들어가고 탈락하는
            # 문제가 있었다(실사례: 방배동 매물이 서초구 쿼터 안에 못 들어 최종 목록에서 빠짐).
            # 카카오 API 호출 없이 계산만 하는 직선거리라 후보 전부(2만 건대)에 적용해도 빠르다.
            # 최종 _refine 단계에서 이 추정치가 아니라 카카오 실제 경로로 다시 한번 정확히 거른다.
            # 버퍼를 안 두는 이유: 이건 어느 자치구를 통째로 버릴지 정하는 1차 필터(버퍼 필요)와
            # 달리 "이 매물 하나"의 정확한 좌표 기준이라, 자치구 대표좌표용 버퍼(20분)를 그대로
            # 쓰면 걸러지는 게 거의 없다(직선거리 추정식 10+거리x2.2 기준 버퍼 20분=13.6km, 서울
            # 웬만한 구 안에서는 다 통과함 - 2026-10-01 실측). 최종 _refine이 카카오 실제 경로로
            # 한 번 더 정확히 거르므로, 여기서 추정치가 약간 낙관적이어도 안전하다.
            estimated_minutes = calculator._estimate_commute_minutes_fallback(
                work_lat, work_lon, listing["lat"], listing["lon"], request.transport_type
            )
            if estimated_minutes > request.max_commute_minutes:
                continue

            # 반전세는 "반전세 포함"을 체크해야 월세 추천에 들어온다
            if listing["lease_type"] == "월세" and listing.get("is_semi_jeonse") and not request.include_semi_jeonse:
                excluded_semi_jeonse += 1
                continue
            # 보유 보증금을 넘는 매물은 대출이 가능할 때만 (모두 표시를 체크하면 이 조건을 건너뛴다)
            if not request.show_all_deposits and (listing["deposit"] or 0) > own_deposit \
                    and not _loan_possible(request, listing, loan_cache):
                excluded_no_loan += 1
                continue

            total_candidates += 1
            listing_id = listing["listing_id"]
            listing_by_id[listing_id] = listing
            # 순위 산출에 필요한 값만 담은 가벼운 레코드 (전체 필드는 top_n으로 추려진 뒤 _expand에서 채운다).
            lightweight = {
                "listing_id": listing_id,
                "region": listing["region"],
                RANK_COST_KEY: _rank_cost(listing, deposit_rate.rate_percent, monthly_deposit_rate.rate_percent),
                "monthly_savings": 0,  # rank_by_real_cost의 동점자 2차 기준. 여기선 항상 0(_to_result와 동일).
                "commute_minutes": estimated_minutes,
                "commute_source": calculator.ESTIMATE_SOURCE_LABEL,
            }
            (wolse_results if listing["lease_type"] == "월세" else jeonse_results).append(lightweight)

    # 월세/전세 각각 보증금전환 실질거주비 낮은 순 상위 N (절감 기준이 없으니 require_savings=False)
    top_n = _top_n(request)
    wolse = data_analysis.rank_by_real_cost(wolse_results, top_n=top_n, require_savings=False, cost_key=RANK_COST_KEY)
    jeonse = data_analysis.rank_by_real_cost(jeonse_results, top_n=top_n, require_savings=False, cost_key=RANK_COST_KEY)

    # 랭킹이 끝나 top_n(최대 TOP_N x 2)으로 추려진 뒤에야 _to_result()로 나머지 필드(브로커/좌표 등)를 채운다.
    def _expand(ranked: list[dict]) -> list[dict]:
        return [
            _to_result(listing_by_id[r["listing_id"]], r["commute_minutes"], r["commute_source"],
                       deposit_rate.rate_percent, request.deposit, monthly_deposit_rate.rate_percent)
            for r in ranked
        ]

    wolse = _expand(wolse)
    jeonse = _expand(jeonse)

    # 최종 목록(top_n으로 추려진 분량)에만 중첩 정보(브로커/대출)를 붙인다 - 통근시간은 카카오 API를
    # 다시 부르지 않고 위에서 이미 계산한 직선거리 추정치를 그대로 쓴다(2026-10-01, 검색/매칭을 가볍게
    # 하는 쪽으로 전환 - 정확한 값은 화면에 보이는 카드만 GET .../commute로 따로 불러온다).
    def _finalize(recommendations: list[dict]) -> list[dict]:
        recommendations = [_clean_nan(r) for r in recommendations]
        for r in recommendations:
            _attach_details(r, listing_by_id[r["listing_id"]])
            # 관리자 화면에서 저장한 대출 조건으로 이 매물에 신청 가능한 대출을 판별한다 (저장된 대출이 없으면 빈 목록).
            # 대출금리표가 없는 대출의 기본금리는 이 매물 계산에 쓴 것과 같은 기준금리 API 값(deposit_rate)을 그대로 쓴다.
            r["eligible_loans"] = loan_matcher.match_eligible_loans(request, r, deposit_rate.rate_percent)
        recommendations.sort(key=lambda r: (r[RANK_COST_KEY], r["listing_id"]))
        return recommendations

    wolse = _finalize(wolse)
    jeonse = _finalize(jeonse)

    # 광고하기 매물 (2026-10-08): 일반 추천과 별개로 만든다. 프론트가 일반 카드 5개마다 다음 칸에 끼워 넣는다.
    ad_wolse, ad_jeonse = _build_ad_cards(
        request, request.ad_listing_ids, {r["listing_id"] for r in wolse + jeonse}, work_lat, work_lon, deposit_limit,
        deposit_rate, monthly_deposit_rate) if request.ad_listing_ids else ([], [])

    # 주거정책 추천: 직장 위치 자치구(work_region) 정책을 기본으로 보여주고, 매물을 클릭하면 그 매물 자치구 정책으로 바꾼다.
    # 지역값이 "서울"인 정책은 어느 자치구든(25개 모두) match_display_policies가 함께 돌려준다. 추천 결과에 나온 자치구와
    # 직장 자치구의 정책만 내려준다 (직장 자치구가 통근권 목록에 없어도 기본 표시가 비지 않게 따로 계산).
    work_region = request.work_location
    if work_region not in policies_by_region:
        policies_by_region[work_region] = policy_matcher.match_display_policies(request, building_region=work_region)
    wanted = {r["region"] for r in wolse + jeonse + ad_wolse + ad_jeonse} | {work_region}
    policies_by_region = {name: pols for name, pols in policies_by_region.items() if name in wanted}

    # 2026-10-01부터 이 응답의 통근시간은 항상 직선거리 추정치다 (모듈 docstring 참고) - 정확한 값은
    # 화면에 보이는 카드만 GET .../commute로 따로 불러온다.
    used_distance_estimate = True
    logger.info(
        f"더미 매물 진단: 후보 {total_candidates}건 -> 월세 {len(wolse)} / 전세 {len(jeonse)} ({time.time() - started:.1f}초)"
    )
    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "wolse_recommendations": wolse,
        "jeonse_recommendations": jeonse,
        "ad_wolse_recommendations": ad_wolse,
        "ad_jeonse_recommendations": ad_jeonse,
        "used_distance_estimate": used_distance_estimate,
        "total_candidates": total_candidates,
        "deposit_limit": deposit_limit,
        "own_deposit": own_deposit,
        "show_all_deposits": bool(request.show_all_deposits),
        "include_semi_jeonse": bool(request.include_semi_jeonse),
        "excluded_no_loan": excluded_no_loan,
        "excluded_semi_jeonse": excluded_semi_jeonse,
        "work_region": work_region,
        "policies_by_region": policies_by_region,
        "deposit_conversion_rate": deposit_rate.rate_percent,
        "deposit_conversion_rate_base": deposit_rate.base_month,
        "deposit_conversion_rate_label": deposit_rate.label,
        "deposit_conversion_rate_is_fallback": deposit_rate.is_fallback,
        "monthly_deposit_rate": monthly_deposit_rate.rate_percent,
        "monthly_deposit_rate_base": monthly_deposit_rate.base_month,
        "monthly_deposit_rate_label": monthly_deposit_rate.label,
        "monthly_deposit_rate_is_fallback": monthly_deposit_rate.is_fallback,
        **rir_fields,
    }
