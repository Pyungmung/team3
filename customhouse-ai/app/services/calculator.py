"""
[담당: 송귀성] 주거비 산출 알고리즘 (핵심 기능)

기획서 공식: 실질 주거비 = 월세 + 관리비 + 대출이자 + 교통비 - 정부지원금
(2026-09-28: 월세는 "대출이자" 항목을 뺐다 - 아래 4번 참고, 전세는 그대로 유지)

처리 흐름 (2026-09-15: 지역 평균 추천 -> 개별 매물(건물) 추천으로 변경):
1. 소득 기준 적정 월세 상한(RIR 30% 가이드라인) 계산
2. 직장 위치 기준 통근시간 <= max_commute_minutes + 이동수단별 버퍼(REGION_PREFILTER_BUFFER_MIN_BY_MODE)
   인 지역만 1차로 넓게 후보 필터링. 통근시간은 1순위로 카카오 API(자동차/대중교통/도보,
   _commute_minutes 참고)의 실제 결과를 쓴다 (2026-09-27: 직선거리 추정만 쓰면 통근권이 실제
   도로망과 무관하게 완벽한 원으로 나오는 문제가 있어 한 번 완전히 제거했었다). 다만 2026-09-28에
   카카오 API가 일일 호출 한도를 소진해 통근시간을 하나도 못 구해 진단이 통째로 0건이 되는 문제가
   생겨서, API가 완전히 막혔을 때만 쓰는 비상용 직선거리 추정을 다시 넣었다(ESTIMATE_SOURCE_LABEL,
   DiagnosisResponse.used_distance_estimate로 프론트에 실측/추정 여부를 표시). 이 1차 필터는
   "자치구 대표좌표(구청 근처) 1곳" 기준이라 넉넉한 버퍼를 두는데, 최종 추천 목록으로 추려진 뒤
   매물 개별 좌표로 통근시간을 다시 정확히 계산해서(_recompute_precise_commute) 진짜 희망
   통근시간을 넘는 매물만 그때 제외한다 - 대표좌표 하나로 뭉뚱그리면 직장 바로 옆 매물도 지역
   전체가 대표값에 걸려 통째로 안 보이는 문제가 있었다 (2026-09-28 발견/수정).
3. 후보 지역마다 국토부 실거래 개별 건물을 후보 매물로 가져옴
   (DATA_GO_KR_API_KEY 미설정이거나 해당 지역에 실거래 내역이 없으면
    지역 평균 샘플값으로 만든 가상 매물 1건으로 폴백)
4. 매물마다 "그 매물 실제 보증금"과 사용자의 보유 보증금을 비교해 대출이자를 계산했었는데,
   2026-09-28부터 월세는 이 계산을 하지 않는다 - 전월세전환율/대출금리 모두 정확한 실제
   금리를 알 수 없는 가정값이라, "실거래 원본 월세/보증금 표시가 가장 중요하다"는 요청에 따라
   월세는 rent=listing_monthly_rent(원본 그대로), loan_interest=0으로 고정한다. 전세는
   보증금이 곧 비용의 핵심이라 비교할 다른 실측 기준이 없으므로, 기존처럼 보증금 차액을
   대출이자로 환산해서 비교한다(사용자 확인).
5. 정책 매칭(policy_matcher)으로 매물 지역·사용자 조건에 맞는 정책을 찾는다.
   2026-09-17: 실제 정책 데이터(docs/housing_policy_list.csv)는 지원혜택이 자유
   텍스트라 정형 수치가 없어 government_support는 항상 0 - "주거정책 추천" 표에
   보여주는 정보성 매칭일 뿐, 아래 공식의 정부지원금 항목에는 반영하지 않는다.
6. data_analysis.rank_by_real_cost가 실질 주거비 오름차순 상위 N개를 추천 리스트로 반환한다.
   전세는 "기존 예상 주거비보다 실제로 더 저렴한" 매물만 고르지만(require_savings=True), 월세는
   4번에서 비교 기준 자체가 없어져 이 필터를 건너뛰고(require_savings=False) 그냥 실거래
   기준 저렴한 순으로 전부 보여준다.
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

# 통근시간은 원래 "자치구 대표좌표(구청 근처) 1곳"만 기준으로 계산했는데, 매물이 대표좌표보다
# 직장에 훨씬 가까워도(혹은 멀어도) 그 구 전체가 대표값 하나로 뭉뚱그려져서, 실제로는 10분
# 거리인 매물이 있어도 대표값이 기준을 넘으면 지역 전체가 통째로 후보에서 빠지는 문제가 있었다
# (2026-09-28 발견 - 직장 바로 옆 매물이 하나도 안 뜨는 현상의 원인). 그래서 1차로는 대표좌표
# 기준에 넉넉한 버퍼를 둬서 지역 후보를 넓게 잡고(이 단계는 여전히 실제 카카오 API 결과, 직선거리
# 추정 아님), 최종 추천 목록으로 추려진 뒤(_recompute_precise_commute) 매물 개별 좌표로 정확한
# 통근시간을 다시 계산해서 진짜 희망 통근시간을 넘는 매물만 제외한다.
#
# 버퍼 크기는 이동수단마다 다르다 - 대표좌표와 실제 위치의 "여분 거리"가 똑같아도, 느린 수단일수록
# 시간 차이가 훨씬 커진다. 실측(2026-09-28): 도보로 자치구 대표좌표까지 39분이 나온 지역이 있었는데
# (실제 그 지역 안 특정 건물은 도보 10분 거리였을 수 있음), 버퍼가 20분뿐이면 39 > 10+20이라 그
# 지역 전체가 1차 필터에서부터 통째로 빠져 도보 모드가 사실상 항상 0건이 되는 문제가 있었다.
REGION_PREFILTER_BUFFER_MIN_BY_MODE = {
    "CAR": 20,
    "PUBLIC": 20,
    "WALK": 90,  # 도보는 시속 ~4~5km라 같은 여분 거리도 자동차/대중교통보다 훨씬 긴 시간으로 나타남
}
DEFAULT_REGION_PREFILTER_BUFFER_MIN = 20

# 2026-09-27에는 "통근권이 실제 도로망과 무관하게 완벽한 원으로 나온다"는 문제로 직선거리 추정을
# 완전히 제거했었는데, 2026-09-28에 카카오 대중교통 API가 일일 호출 한도를 소진해서 그 이후로는
# 아예 통근시간을 하나도 계산 못 해 진단 결과가 통째로 0건이 되는 새 문제가 생겼다. 그래서
# "1순위는 항상 실제 API, API가 안 될 때만 비상용으로 직선거리 추정"으로 바꾼다 - 위 두 문제를
# 동시에 피하려면 기본값은 실제 API를 계속 쓰되, 완전히 막혔을 때만 추정치로 서비스가 죽지 않게
# 한다. 어떤 값을 썼는지는 commute_source에 그대로 남아(라벨에 ESTIMATE_SOURCE_LABEL 포함)
# DiagnosisResponse.used_distance_estimate로 요약해서 프론트에 표시한다 - 사용자가 "이 결과가
# 실측인지 추정인지" 알 수 있어야 한다는 요청 반영.
ESTIMATE_SOURCE_LABEL = "직선거리 추정(비상용)"
FALLBACK_COMMUTE_BASE_MINUTES = 10
FALLBACK_COMMUTE_MINUTES_PER_KM = 2.2

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


def _estimate_commute_minutes_fallback(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    """카카오 API가 전부 막혔을 때만 쓰는 비상용 직선거리 추정치(ESTIMATE_SOURCE_LABEL 참고).
    이동수단을 구분하지 않는 단순 근사식이라 실제 도로/노선과는 차이가 클 수 있다 - 정상적으로는
    _commute_minutes가 카카오 API 결과를 우선하고, 이 함수는 그게 전부 실패할 때만 호출된다."""
    distance_km = _haversine_km(lat1, lon1, lat2, lon2)
    return round(FALLBACK_COMMUTE_BASE_MINUTES + distance_km * FALLBACK_COMMUTE_MINUTES_PER_KM)


def _commute_minutes(
    work_lat: float, work_lon: float, dest_lat: float, dest_lon: float, transport_type: str | None
) -> tuple[int | None, str]:
    """통근시간(분)과 산출 방식을 함께 돌려준다. 1순위는 항상 "주요 통근 수단"(transport_type)에
    맞는 카카오 API의 실제 경로 결과다 (2026-09-27: 직선거리 추정만 쓰면 통근권이 실제 도로/노선과
    무관하게 완벽한 원 모양으로 나오는 문제가 있어 원래는 완전히 제거했었다).
    - CAR: 카카오모빌리티 길찾기(자동차 전용, kakao_mobility.py)
    - PUBLIC: 카카오맵 대중교통 경로 조회 (여러 경로 중 가장 빠른 것)
    - WALK: 카카오맵 도보 경로 조회 (목적지가 너무 멀면 결과 없음)

    API 키 미설정/호출 실패/결과 없음(예: 도보로 가기엔 너무 먼 거리, 출발지=도착지 등)이면,
    2026-09-28부터는 그 자리에서 바로 실패 처리하지 않고 직선거리 기반 비상용 추정치로 폴백한다
    (그날 카카오 대중교통 API가 일일 한도를 소진해서 진단 결과가 통째로 0건이 되는 문제가 있었다 -
    ESTIMATE_SOURCE_LABEL 참고). None을 돌려주는 경우는 이제 없다 - 항상 (분, 산출방식) 튜플이다.
    """
    if transport_type == "CAR":
        if kakao_mobility.is_enabled():
            try:
                minutes = kakao_mobility.fetch_car_commute_minutes(dest_lat, dest_lon, work_lat, work_lon)
                return minutes, "카카오 길찾기 API(자동차)"
            except kakao_mobility.KakaoMobilityError as e:
                logger.warning(str(e))
        return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon), ESTIMATE_SOURCE_LABEL

    if transport_type == "WALK":
        if kakao_routing.is_enabled():
            try:
                minutes = kakao_routing.fetch_walk_minutes(dest_lat, dest_lon, work_lat, work_lon)
                return minutes, "카카오맵 API(도보)"
            except kakao_routing.KakaoRoutingError as e:
                logger.warning(str(e))
        return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon), ESTIMATE_SOURCE_LABEL

    # PUBLIC(기본값) 및 그 외 인식하지 못하는 값은 모두 대중교통 기준으로 계산한다.
    if kakao_routing.is_enabled():
        try:
            minutes = kakao_routing.fetch_public_transit_minutes(dest_lat, dest_lon, work_lat, work_lon)
            return minutes, "카카오맵 API(대중교통)"
        except kakao_routing.KakaoRoutingError as e:
            logger.warning(str(e))
    return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon), ESTIMATE_SOURCE_LABEL


def _recompute_precise_commute(
    recommendations: list[dict], work_lat: float, work_lon: float, transport_type: str | None, max_commute_minutes: int
) -> list[dict]:
    """최종 추천 목록(resolve_coordinates로 lat/lon까지 구해진 뒤)에서, 좌표가 있는 매물은
    "자치구 대표좌표"가 아니라 그 매물 자신의 정확한 위치로 통근시간을 다시 계산해서
    commute_minutes/commute_source를 갱신하고, 진짜 희망 통근시간(max_commute_minutes, 버퍼
    없는 정확한 값)을 넘으면 제외한다. 좌표가 없는 매물(단독다가구 등)은 기존 지역 대표값이
    이미 (버퍼 없이) max_commute_minutes 이내인지로 판단한다 - REGION_PREFILTER_BUFFER_MIN_BY_MODE 참고.

    이걸 최종 목록(최대 500건)에만 하는 이유는 juso_api/kakao_geocode와 동일 - 원본 후보
    전체에 대해 통근시간 API를 다시 호출하면 카카오 API에 과도한 동시 요청이 몰린다."""
    with_coords = [r for r in recommendations if r.get("lat") is not None and r.get("lon") is not None]

    def _check(r: dict) -> tuple[int, int | None, str]:
        minutes, source = _commute_minutes(work_lat, work_lon, r["lat"], r["lon"], transport_type)
        return id(r), minutes, source

    with ThreadPoolExecutor(max_workers=16) as pool:
        precise_results = {rid: (minutes, source) for rid, minutes, source in pool.map(_check, with_coords)}

    kept = []
    for r in recommendations:
        if r.get("lat") is not None and r.get("lon") is not None:
            minutes, source = precise_results[id(r)]
            if minutes is None or minutes > max_commute_minutes:
                continue
            r["commute_minutes"] = minutes
            r["commute_source"] = source
        elif r["commute_minutes"] > max_commute_minutes:
            continue  # 좌표 없는 매물은 지역 대표값(버퍼 없는 원래 값) 기준으로 그대로 판단
        kept.append(r)
    return kept


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
                "road_address": None,
                "jibun_address": "",
                "jibun_search_keyword": "",
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

    # 통근권 안에 드는 지역만 먼저 추린다. 지역마다 카카오 API를 호출해야 해서(네트워크 호출)
    # 순차로 하면 느려지므로, 실거래 조회와 마찬가지로 스레드풀로 동시에 계산한다.
    def _region_commute(region: RegionMeta) -> tuple[int | None, str]:
        return _commute_minutes(work_lat, work_lon, region.lat, region.lon, request.transport_type)

    with ThreadPoolExecutor(max_workers=16) as pool:
        commute_results = list(pool.map(_region_commute, regions))

    for region, (minutes, source) in zip(regions, commute_results):
        if source == ESTIMATE_SOURCE_LABEL:
            logger.warning(f"카카오 API 사용 불가로 비상용 직선거리 추정 사용: {region.name} ({minutes}분 추정)")

    # 여기서는 이동수단별 버퍼(REGION_PREFILTER_BUFFER_MIN_BY_MODE)만큼 넉넉하게 잡는다 -
    # 최종적으로 사용자가 원하는 정확한 max_commute_minutes는 매물 개별 좌표로 재계산한 뒤
    # (_recompute_precise_commute)에 적용한다. 이 1차 필터는 "이 지역 매물을 아예 안 볼지
    # 말지"만 정하는 넓은 그물이다.
    prefilter_buffer = REGION_PREFILTER_BUFFER_MIN_BY_MODE.get(
        request.transport_type, DEFAULT_REGION_PREFILTER_BUFFER_MIN
    )
    eligible = [
        (region, minutes, source)
        for region, (minutes, source) in zip(regions, commute_results)
        if minutes <= request.max_commute_minutes + prefilter_buffer
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

            maintenance_fee = region.maintenance_fee

            # 1) 매물 실제 보증금 반영 - 2026-09-28: 월세는 "보증금을 조정해서 월세를 조정"하는
            # 계산(전월세전환율/대출금리 가정)을 더 이상 쓰지 않는다. 정확한 금리를 알 수 없는
            # 불확실한 가정으로 실거래 원본을 왜곡하는 것보다, 실거래 월세/보증금을 있는 그대로
            # 보여주는 게 가장 중요하다는 요청에 따른 것이다 - rent는 항상 실거래 원본과 같고,
            # baseline_cost도 real_housing_cost와 같게 둬서(비교 기준 자체가 없음) 절감액이
            # 0으로 나온다. 전세는 보증금이 곧 비용의 핵심이라 비교할 다른 실측 기준이 없으므로,
            # 보증금 차액을 대출이자로 환산해 비교하던 기존 방식을 그대로 유지한다(사용자 확인).
            if building["lease_type"] == "전세":
                deposit_gap = building["deposit"] - effective_deposit
                if deposit_gap <= 0:
                    surplus = -deposit_gap
                    rent = max(0, building["monthly_rent"] - round(surplus * conversion_rate_monthly))
                    loan_interest = 0
                else:
                    rent = building["monthly_rent"]
                    loan_interest = round(deposit_gap * market_loan_rate_monthly)
                baseline_rent = request.desired_rent if request.desired_rent is not None else building["monthly_rent"]
                baseline_loan_interest = round(deposit_gap * market_loan_rate_monthly) if deposit_gap > 0 else 0
                baseline_cost_override = baseline_rent + maintenance_fee + baseline_loan_interest + transportation_cost
            else:
                rent = building["monthly_rent"]
                loan_interest = 0
                baseline_cost_override = None

            # 2) 정책 매칭 - "주거정책 추천" 표에 보여줄 정보성 매칭 (매물 지역 기준).
            # CSV 실데이터는 지원혜택이 자유 텍스트라 정형 수치가 없어 government_support는
            # 항상 0 - policy_matcher.py 모듈 docstring 참고.
            matched_policies = policy_matcher.match_display_policies(request, building_region=region.name)
            government_support = 0

            real_housing_cost = rent + maintenance_fee + loan_interest + transportation_cost - government_support
            baseline_cost = baseline_cost_override if baseline_cost_override is not None else real_housing_cost
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
                    # 아래 4개는 최종 추천 목록으로 추려진 뒤 resolve_addresses()가 "address"/
                    # "building_name"을 채우거나 보완하는 데 쓰는 중간 필드다 (원본 후보 전체에
                    # 대해 미리 하지 않는 이유는 api_collector.fetch_candidate_buildings 주의사항 참고).
                    "unnamed": building["unnamed"],
                    "road_address": building["road_address"],
                    "jibun_address": building["jibun_address"],
                    "jibun_search_keyword": building["jibun_search_keyword"],
                    "lat": None,  # resolve_coordinates()가 지오코딩 성공 시 채운다 (실패/미설정이면 None 유지)
                    "lon": None,
                    "exclusive_area": building["exclusive_area"],
                    "floor": building["floor"],
                    "deal_date": building["deal_date"],
                    "lease_type": building["lease_type"],  # "전세" | "월세"
                    "is_semi_jeonse": is_semi_jeonse,  # 월세인데 보증금이 커서 반전세 성격인 매물
                    "commute_minutes": commute,
                    "commute_source": commute_source,  # 보통 카카오 API 실측, API가 막히면 ESTIMATE_SOURCE_LABEL(비상용 추정)
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

    # 완전 무제한(top_n=None)으로 두면 조건에 맞는 매물이 지역·유형에 따라 수만 건까지 나올 수
    # 있어(2026-09-27 실측: 월세 1만+/전세 8천+, 응답 52MB) 지도 마커/리스트 렌더링이 멈춘다.
    # 그래서 상한선을 둬서 "조건에 맞는 건 최대한 다 보여주되" 브라우저가 버틸 수 있게 한다.
    #
    # 지역별 분배: 예전엔 지역마다 고정 상한(per_region_limit)을 둬서 시세 낮은 지역이 순위를
    # 독식하는 걸 막았는데, 상한을 고정값으로 두면 "그 상한이 top_n보다 작아야만" 효과가 있고
    # (2026-09-28 실측: 500/500으로 나란히 두니 상한이 사실상 무의미해져서 광진구는 절감 매물이
    # 410건이나 있었는데 최종 15건만 남았다 - 시세가 비교적 높은 지역은 절감해도 절대금액이 커서
    # "실질 주거비 오름차순 전체 정렬"에서 불리하다), 그렇다고 상한을 낮게 고정하면 "조건에 맞는
    # 건 전부 보여달라"는 요청과 충돌한다. 그래서 고정 상한 대신 rank_by_real_cost가 지역별
    # 절감 매물 수에 "비례"해서 top_n을 나눠 배분한다 - 매물 많은 지역은 그만큼 더 많이, 광진구·
    # 중구처럼 적은 지역도 자기 몫만큼은 보장된다 (data_analysis.py의 _proportional_allocate 참고).
    TOP_N = 1000
    # require_savings=False: 월세는 보증금 조정 계산이 없어져 monthly_savings가 항상 0이라
    # 절감액 필터를 적용하면 전부 걸러진다 - 그냥 실거래 기준 저렴한 순으로 보여준다.
    # 전세는 보증금 차액을 대출이자로 환산해 비교하는 기존 방식을 유지하므로 필터도 그대로 둔다.
    wolse_recommendations = data_analysis.rank_by_real_cost(wolse_results, top_n=TOP_N, require_savings=False)
    jeonse_recommendations = data_analysis.rank_by_real_cost(jeonse_results, top_n=TOP_N, require_savings=True)

    # 주소(juso_api.py) 변환은 여기, 즉 예산 필터링·정렬·top_n까지 끝난 "최종 추천 목록"에만
    # 한다 - 원본 후보 전체(지역당 최대 수천 건)에 대해 하면 통근범위가 넓은 진단 1건이 juso.go.kr에
    # 과도한 동시 요청을 보내 수십~수백 초까지 느려질 수 있다 (2026-09-28 실측, api_collector.py
    # fetch_candidate_buildings 주의사항 참고).
    api_collector.resolve_addresses(wolse_recommendations)
    api_collector.resolve_addresses(jeonse_recommendations)

    # 주소가 잡힌 매물은 카카오 로컬 API로 실제 좌표(lat/lon)까지 구해서, 지도에서 "지역당 마커
    # 1개" 대신 매물별 정확한 위치에 핀을 찍을 수 있게 한다 (map-util.js 참고). 이것도 최종
    # 추천 목록에만 호출한다 - 이유는 resolve_addresses와 동일.
    api_collector.resolve_coordinates(wolse_recommendations)
    api_collector.resolve_coordinates(jeonse_recommendations)

    # 지금까지의 commute_minutes는 "자치구 대표좌표" 기준(+버퍼)으로 지역을 넓게 추린 값이다.
    # 이제 좌표가 확보된 매물은 그 매물 자신의 위치로 통근시간을 정확히 다시 계산해서, 진짜
    # 희망 통근시간을 넘는 매물은 걸러내고 남은 매물의 commute_minutes를 정확한 값으로 갱신한다
    # (REGION_PREFILTER_BUFFER_MIN, _recompute_precise_commute 참고 - 2026-09-28: 직장 바로
    # 옆 매물이 "자치구 대표값"에 걸려 통째로 안 보이던 문제의 근본 수정).
    wolse_recommendations = _recompute_precise_commute(
        wolse_recommendations, work_lat, work_lon, request.transport_type, request.max_commute_minutes
    )
    jeonse_recommendations = _recompute_precise_commute(
        jeonse_recommendations, work_lat, work_lon, request.transport_type, request.max_commute_minutes
    )

    # 이번 진단에서 통근시간 중 하나라도 비상용 직선거리 추정(ESTIMATE_SOURCE_LABEL)을 썼는지 -
    # 프론트가 화면 한구석에 "API 실측 기반" / "일부 직선거리 추정 사용됨"을 표시하는 데 쓴다.
    used_distance_estimate = any(
        r["commute_source"] == ESTIMATE_SOURCE_LABEL for r in wolse_recommendations + jeonse_recommendations
    )

    return {
        "affordable_rent": affordable_rent,
        "rent_to_income_ratio": rir,
        "wolse_recommendations": wolse_recommendations,
        "jeonse_recommendations": jeonse_recommendations,
        "used_distance_estimate": used_distance_estimate,
    }
