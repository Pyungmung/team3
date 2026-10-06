"""
[담당: 송귀성] 통근시간/지역 좌표 계산 공용 헬퍼

원래 이 파일에 있던 국토부 실거래가 기준 진단(run_diagnosis, /api/v1/diagnosis)은
더미 매물 기반 추천(listing_recommender.py, /api/v1/diagnosis/listings)으로 완전히
대체되어 삭제됐다. 이 파일에 남은 건 그 listing_recommender.py와
app/api/v1/listing_commute.py가 가져다 쓰는 공용 헬퍼뿐이다:
- _load_regions/_load_lawd_codes/_load_region_coords/_load_work_locations: 지역/좌표 데이터 로딩
- _estimate_commute_minutes_fallback: 직선거리 기반 통근시간 추정 (listing_recommender.py의
  검색/필터/랭킹 전 단계가 이 함수만 쓴다 - 2026-10-01부터 카카오 API 호출 없이 가볍게 처리)
- _commute_minutes: 카카오 API 기반 정확한 통근시간 계산(실패 시 위 추정치로 폴백) - 화면에
  "보이는" 매물 카드 1건의 통근시간을 그때그때 다시 불러오는 listing_commute.py 전용
- get_work_hubs: 직장 위치 드롭다운 목록 (/api/v1/policy/work-hubs가 사용)
"""
import json
import logging
import math
from dataclasses import dataclass

from app.core.config import settings
from app.services import kakao_mobility, kakao_routing

logger = logging.getLogger(__name__)

# 통근시간은 원래 "자치구 대표좌표(구청 근처) 1곳"만 기준으로 계산했는데, 매물이 대표좌표보다
# 직장에 훨씬 가까워도(혹은 멀어도) 그 구 전체가 대표값 하나로 뭉뚱그려져서, 실제로는 10분
# 거리인 매물이 있어도 대표값이 기준을 넘으면 지역 전체가 통째로 후보에서 빠지는 문제가 있었다
# (2026-09-28 발견 - 직장 바로 옆 매물이 하나도 안 뜨는 현상의 원인). 그래서 자치구 대표좌표
# 기준에는 넉넉한 버퍼를 둬서 지역 후보를 넓게 잡고(어느 CSV를 읽을지만 정하는 IO 절약용),
# "이 매물 자신의" 좌표 기준으로 다시 한번 걸러서 진짜 희망 통근시간을 넘는 매물을 제외한다
# (listing_recommender.py 참고). 2026-10-01부터 이 전 단계가 카카오 API 호출 없이 직선거리
# 추정(_estimate_commute_minutes_fallback)만으로 돌아가므로 이동수단별 버퍼 구분은 쓰지 않는다 -
# 화면에 보이는 카드의 정확한(카카오 API) 통근시간은 listing_commute.py가 매물 1건씩 따로 조회한다.
# 2026-10-06: 추정식이 이동수단별로 달라지면서(아래 COMMUTE_ESTIMATE_PARAMS) 버퍼를 "분"이 아니라 "거리(km)"로 둔다 -
# 도보처럼 1km가 20분인 수단에서 분 버퍼는 자치구 대표좌표 오차(수 km)를 감당하지 못한다.
REGION_PREFILTER_BUFFER_KM = 10.0

# listing_commute.py가 매물 카드 1건의 정확한 통근시간을 조회할 때, 카카오 API 키 미설정/호출
# 실패/결과 없음이면 그 자리에서 실패 처리하지 않고 이 직선거리 기반 비상용 추정치로 폴백한다.
# 어떤 값을 썼는지는 commute_source에 그대로 남는다(라벨에 ESTIMATE_SOURCE_LABEL 포함).
ESTIMATE_SOURCE_LABEL = "직선거리 추정(비상용)"
# 직선거리(km) -> 통근시간(분) 추정식. 이동수단별로 카카오 실제 경로 시간을 대량 측정해 회귀한 평균 보정식이다
# (2026-10-06, 서울 10개 직장 거점 x 다양한 거리의 실제 매물에서 대중교통 396건/자동차 300건/도보 119건).
# 가까운 거리는 기울기가 가파르고(승차/대기/환승 같은 고정 시간 때문에 처음 2km 안에서 빨리 늘어난다) 멀어질수록 완만해서,
# 직선 하나로 맞추면 아주 가까운 매물이 실제보다 3분쯤 길게 나왔다(예: 대중교통 0.5~1km 실측 평균 12.5분 vs 직선식 15.8분).
# 그래서 2km에서 한 번 꺾이는 2구간 직선으로 맞췄다: 분 = 기본분 + 가까운구간기울기 x min(km, 꺾임) + 먼구간기울기 x max(0, km - 꺾임).
# 하한(MIN)은 실측 최솟값이다 (대중교통은 아무리 가까워도 10분 안팎이 걸린다).
#   대중교통: 7.6 + 6.39 x km(2km까지) + 2.53 x (2km 초과분)   평균 절대오차 4.1분, 5분 이내 69%
#   자동차:   1.3 + 6.27 x km(2km까지) + 2.37 x (2km 초과분)   평균 절대오차 4.2분, 5분 이내 68%
#   도보:     1.9 + 20.4 x km (직선 하나로 충분, R² 0.94)       평균 절대오차 3.5분 - 이전 식은 도보를 평균 26분 낮게 추정했다
# 이전 식(10 + 2.2 x km)은 대중교통을 평균 7.8분 낮게 추정했다. 평균에 맞춘 식이라 개별 매물은 +-5분(약 70%)~10분(약 93%) 안에서
# 어긋난다 - 정확한 값은 화면에 보이는 카드가 따로 조회해 바꿔 보여준다.
# (기본분, 가까운구간 분/km, 꺾임 km, 먼구간 분/km, 최소 분)
COMMUTE_ESTIMATE_PARAMS = {
    "PUBLIC": (7.6, 6.39, 2.0, 2.53, 10),
    "CAR": (1.3, 6.27, 2.0, 2.37, 4),
    "WALK": (1.9, 20.36, 99.0, 20.36, 1),
}

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


def _commute_params(transport_type: str | None) -> tuple[float, float, float, float, int]:
    """이동수단별 추정식 계수. PUBLIC(기본값) 및 인식하지 못하는 값은 대중교통 기준이다 (_commute_minutes와 같은 규칙)."""
    return COMMUTE_ESTIMATE_PARAMS.get(transport_type or "PUBLIC", COMMUTE_ESTIMATE_PARAMS["PUBLIC"])


def _minutes_for_distance(distance_km: float, transport_type: str | None) -> float:
    base, near_slope, knot_km, far_slope, floor = _commute_params(transport_type)
    minutes = base + near_slope * min(distance_km, knot_km) + far_slope * max(0.0, distance_km - knot_km)
    return max(minutes, floor)


def _estimate_commute_minutes_fallback(lat1: float, lon1: float, lat2: float, lon2: float,
                                       transport_type: str | None = None) -> int:
    """직선거리 기반 통근시간 추정치(ESTIMATE_SOURCE_LABEL 참고) - 검색/매칭 단계는 이 값만 쓰고(카카오 호출 없음),
    카카오 API가 막혔을 때의 폴백이기도 하다. 이동수단별 평균 보정식(COMMUTE_ESTIMATE_PARAMS)이라 개별 매물은
    실제 경로 시간과 몇 분 차이가 난다 - 정확한 값은 listing_commute.py가 매물 1건씩 따로 조회한다."""
    return round(_minutes_for_distance(_haversine_km(lat1, lon1, lat2, lon2), transport_type))


def max_commute_distance_km(max_commute_minutes: float, transport_type: str | None = None) -> float:
    """희망 최대 통근시간(분)을 추정식으로 환산한 직장-매물 직선거리(km) 상한 (자치구 1차 필터용). _minutes_for_distance의 역함수."""
    base, near_slope, knot_km, far_slope, floor = _commute_params(transport_type)
    knot_minutes = base + near_slope * knot_km
    if max_commute_minutes <= knot_minutes:
        return max(0.0, (max_commute_minutes - base) / near_slope)
    return knot_km + (max_commute_minutes - knot_minutes) / far_slope


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
        return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon, transport_type), ESTIMATE_SOURCE_LABEL

    if transport_type == "WALK":
        if kakao_routing.is_enabled():
            try:
                minutes = kakao_routing.fetch_walk_minutes(dest_lat, dest_lon, work_lat, work_lon)
                return minutes, "카카오맵 API(도보)"
            except kakao_routing.KakaoRoutingError as e:
                logger.warning(str(e))
        return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon, transport_type), ESTIMATE_SOURCE_LABEL

    # PUBLIC(기본값) 및 그 외 인식하지 못하는 값은 모두 대중교통 기준으로 계산한다.
    if kakao_routing.is_enabled():
        try:
            minutes = kakao_routing.fetch_public_transit_minutes(dest_lat, dest_lon, work_lat, work_lon)
            return minutes, "카카오맵 API(대중교통)"
        except kakao_routing.KakaoRoutingError as e:
            logger.warning(str(e))
    return _estimate_commute_minutes_fallback(work_lat, work_lon, dest_lat, dest_lon, transport_type), ESTIMATE_SOURCE_LABEL


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


def get_work_hubs() -> list[str]:
    return list(_load_work_locations().keys())


