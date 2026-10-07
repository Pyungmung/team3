"""
[담당: 송귀성] 매물 카드 "실거래 참고" 실시간 조회 API
GET /api/v1/listings/{listing_id}/reference - CSV에 미리 박아둔 고정값 대신, 매물 카드가 뜨자마자
(펼치기 없이, 2026-10-01) 그 매물의 법정동+지번으로 국토교통부 전월세 실거래가 API를 실시간
조회한다(api_collector.fetch_reference_transactions, 최근 2년 중 매물당 최대 5건).
listing_id만으로 자치구를 알아내(listing_repository.get_listing이 번호 접두어로 찾는다) CSV에서
그 매물의 법정동/지번/매물유형을 읽는다 - 프론트가 넘긴 값이 아니라 항상 서버가 가진 원본을 쓴다.

단독·다가구는 국토교통부 API 응답 자체에 지번이 없어(2026-09-30 실측 확인) 지번으로는 "이 건물"을
특정할 방법이 없다 - 행정안전부 도로명주소나 SGIS 등 다른 공공 API를 더 엮어도 원본에 없는 지번을
복원할 수는 없다(검토 완료). 대신 더미 매물(gen_dummyhouses.py)은 "이 매물이 어느 실거래 건을
본떠 만들어졌는지" 정답을 CSV에 들고 있다(ref_계약일/ref_보증금/ref_월세 - 표시용으로 랜덤화된
매물 자체 가격과 달리 원본 그대로) - 그 계약이 있었던 달만 받아 같은 동+보증금+월세+계약일로
걸러내면 지번 없이도 원본 거래 그 자체를 유일하게 다시 찾아낼 수 있다(fetch_by_signature,
광진구 254건 중 정확히 1건으로 검증, 2026-09-30). 신규 등록 매물(Part C)은 이런 원본 거래가
없으므로 이 경로를 타지 않고, 지번이 있으면 건물 단위, 없으면(단독다가구) 동 단위 참고로 간다.
그래서 우선순위는 1) 지번 일치(fetch_reference_transactions) 2) 더미 매물의 원본 거래 재발견
(fetch_by_signature, ref_* 있을 때만) 3) 동 단위 참고(fetch_neighborhood_transactions) 4) 빈 목록 -
응답의 scope 필드로 프론트가 "이 건물의 실거래"인지(building) "동 단위 참고"인지(neighborhood)
구분해서 보여준다.
"""
import json

from fastapi import APIRouter

from app.core.config import settings
from app.models.response_schema import ReferenceTransaction, ReferenceTransactionsResponse
from app.services import api_collector, listing_repository, listing_schema

router = APIRouter(prefix="/listings", tags=["listing-reference"])


def _lawd_cd(region: str) -> str | None:
    with open(settings.lawd_codes_file, encoding="utf-8") as f:
        codes = json.load(f)["regions"].get(region)
    return codes[0] if codes else None


def _extract_jibun(jibun_address: str, region: str, dong: str) -> str:
    """"서울특별시 강남구 개포동 649-4" 같은 지번주소에서 순수 지번("649-4")만 뽑는다.
    아파트처럼 지번 뒤에 단지명이 붙는 경우("165 신반포르엘")도 있어 첫 토큰만 취한다 - 지번
    자체(숫자+선택적 "-숫자")에는 공백이 없으므로 안전하다. 형식이 다르면(단독다가구처럼 애초에
    지번주소가 없는 매물 등) 빈 문자열."""
    prefix = f"서울특별시 {region} {dong} "
    if jibun_address and jibun_address.startswith(prefix):
        return jibun_address[len(prefix):].strip().split(" ", 1)[0]
    return ""


def _to_response(records: list[dict], property_type: str, jibun: str) -> list[ReferenceTransaction]:
    return [
        ReferenceTransaction(
            contract_date=f"{r.get('dealYear')}-{int(r.get('dealMonth', 0)):02d}-{int(r.get('dealDay', 0)):02d}",
            contract_type=r.get("contractType", ""),
            contract_term=r.get("contractTerm", ""),
            use_rr_right="Y" if r.get("useRRRight") == "사용" else "N",
            pre_deposit=_to_int(r.get("preDeposit")),
            pre_monthly_rent=_to_int(r.get("preMonthlyRent")),
            deposit=_to_int(r.get("deposit")),
            monthly_rent=_to_int(r.get("monthlyRent")),
            lease_kind="전세" if _to_int(r.get("monthlyRent")) == 0 else "월세",
            area=_to_float(r.get(_area_field(property_type))),
            floor=(r.get("floor") or "").strip(),
            jibun=(r.get("jibun") or jibun or "").strip(),
        )
        for r in records
    ]


@router.get("/{listing_id}/reference", response_model=ReferenceTransactionsResponse)
def get_reference_transactions(listing_id: str) -> ReferenceTransactionsResponse:
    listing = listing_repository.get_listing(listing_id)
    if listing is None or listing["listing_status"] == listing_schema.LISTING_STATUS_DELETED:
        # 매물 자체가 없거나 삭제 상태다 - 관심매물 카드가 "삭제된 매물" 안내를 띄우는 데 쓴다 (2026-10-06, 상태 확인 10-07)
        return ReferenceTransactionsResponse(scope="deleted", transactions=[])

    lawd_cd = _lawd_cd(listing["region"])
    property_type = listing["property_type"]
    if not lawd_cd or not property_type or not api_collector.is_enabled():
        return ReferenceTransactionsResponse(scope="none", transactions=[])

    # 단독다가구는 국토부 응답 자체에 지번이 없어 지번 매칭이 100% 실패한다(has_jibun=False) -
    # 시도조차 하지 않는다. 최근 2년치를 통째로 헛조회하느라 느려지기만 한다(2026-10-01 실측 9~10초).
    has_jibun = api_collector.PROPERTY_TYPES.get(property_type, {}).get("has_jibun", True)
    jibun = _extract_jibun(listing["jibun_address"], listing["region"], listing["dong"]) if has_jibun else ""
    if jibun:
        records = api_collector.fetch_reference_transactions(lawd_cd, property_type, listing["dong"], jibun)
        if records:
            return ReferenceTransactionsResponse(scope="building", transactions=_to_response(records, property_type, jibun))

    # 지번으로 못 찾았고(단독다가구는 애초에 지번이 없음) 더미 매물이라 원본 거래 정답(ref_*)을
    # 갖고 있으면, 그 계약이 있었던 달의 동+보증금+월세+계약일 조합으로 원본을 재발견한다.
    signature = _signature_match(listing, lawd_cd, property_type)
    if signature:
        return ReferenceTransactionsResponse(scope="building", transactions=_to_response(signature, property_type, ""))

    # 그마저 안 되면(신규 등록 매물이거나 조합이 어긋난 경우) 같은 법정동 단위로 한 번 더 찾아본다.
    neighborhood = api_collector.fetch_neighborhood_transactions(lawd_cd, property_type, listing["dong"])
    if neighborhood:
        return ReferenceTransactionsResponse(scope="neighborhood", transactions=_to_response(neighborhood, property_type, ""))

    return ReferenceTransactionsResponse(scope="none", transactions=[])


def _signature_match(listing: dict, lawd_cd: str, property_type: str) -> list[dict]:
    """더미 매물에만 있는 ref_계약일/ref_보증금/ref_월세로 원본 거래를 재발견한다(신규 등록
    매물은 ref_contract_date가 비어있으므로 이 경로를 타지 않는다)."""
    contract_date = (listing.get("ref_contract_date") or "").strip()
    if len(contract_date) != 10:
        return []
    deposit = _to_int(listing.get("ref_deposit"))
    monthly_rent = _to_int(listing.get("ref_monthly_rent"))
    if deposit is None or monthly_rent is None:
        return []
    deal_ymd = contract_date[:4] + contract_date[5:7]
    deal_day = int(contract_date[8:10])
    return api_collector.fetch_by_signature(lawd_cd, property_type, listing["dong"], deal_ymd, deal_day, deposit, monthly_rent)


def _to_int(raw) -> int | None:
    v = _to_float(raw)
    return round(v) if v is not None else None


def _to_float(raw) -> float | None:
    if raw is None:
        return None
    try:
        return float(str(raw).replace(",", "").strip())
    except ValueError:
        return None


def _area_field(property_type: str) -> str:
    return api_collector.PROPERTY_TYPES.get(property_type, {}).get("area_field", "")
