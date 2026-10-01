"""
[담당: 송귀성] 매물 카드 "통근 예상시간" 정확도 보강 API
GET /api/v1/listings/{listing_id}/commute - 추천 목록 응답(listing_recommender.py)의 commute_minutes는
2026-10-01부터 검색/매칭 전체 단계를 가볍게 하려고 전부 직선거리 추정치다(카카오 API 호출 없음,
calculator._estimate_commute_minutes_fallback). 화면에 "보이는" 매물 카드에 대해서만(report-listings.html이
스크롤로 로딩한 묶음에만, IntersectionObserver 없이도 무한스크롤 배치 단위로 충분) 이 API로 그 매물
하나의 정확한 카카오 API 통근시간을 불러와 표시를 갱신한다 - listing_reference.py의 "카드 1건씩
실시간 조회" 패턴과 동일하다.
"""
from fastapi import APIRouter, HTTPException, Query

from app.models.response_schema import ListingCommuteResponse
from app.services import calculator, listing_repository

router = APIRouter(prefix="/listings", tags=["listing-commute"])


@router.get("/{listing_id}/commute", response_model=ListingCommuteResponse)
def get_listing_commute(
    listing_id: str,
    work_lat: float = Query(alias="workLat"),
    work_lon: float = Query(alias="workLon"),
    transport_type: str | None = Query(None, alias="transportType"),
) -> ListingCommuteResponse:
    listing = listing_repository.get_listing(listing_id)
    if listing is None or listing["lat"] is None or listing["lon"] is None:
        raise HTTPException(status_code=404, detail="매물 위치 정보를 찾을 수 없어요.")
    minutes, source = calculator._commute_minutes(work_lat, work_lon, listing["lat"], listing["lon"], transport_type)
    return ListingCommuteResponse(commute_minutes=minutes, commute_source=source)
