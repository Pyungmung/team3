"""
[담당: 송귀성] 매물 등록/삭제 API
POST /api/v1/listings/register - 로그인한 회원이 직접 매물 1건을 등록한다(백엔드가 인증을 먼저 확인해
전달). 입력한 주소를 listing_builder.resolve_keyword(행안부 도로명주소 + 카카오 좌표, 신규 등록처럼
소량일 때 쓰라고 이미 만들어져 있던 함수)로 해석해 자치구/법정동/도로명주소/좌표/우편번호를 채우고,
listing_repository.append_listing으로 그 자치구 CSV에 새 행을 추가한다.

참고 실거래(ref_*)는 채우지 않는다 - listing_reference.py의 실시간 조회가 이 매물의 법정동+지번으로
그때그때 조회하므로, 등록 시점에 미리 붙여둘 필요가 없다(더미 매물도 똑같은 방식으로 조회된다).

PUT /api/v1/listings/{listing_id}/status - 매물을 삭제 상태(LISTING_STATUS_DELETED)로 바꾼다
(물리 삭제가 아니라 상태 변경 - listing_repository.update_listing_status). 삭제 권한(본인 등록/관리자)
판정은 백엔드(Spring)가 이미 끝낸 뒤 호출하므로, 여기서는 별도 권한 검사를 하지 않는다.

GET /api/v1/listings/{listing_id} - 매물 1건의 원본 정보(진단 계산값 없이)를 그대로 돌려준다. 마이페이지
"등록한 매물 관리" 탭과 매물 수정 폼 프리필용 - 둘 다 진단 조건과 무관하게 내 매물을 봐야 해서 추천
파이프라인을 타지 않는다.

PUT /api/v1/listings/{listing_id} - 매물 수정. 등록과 같은 방식으로 주소를 다시 해석한다(좌표/법정동/
지번이 바뀌었을 수 있어서). 자치구가 바뀌는 주소로는 수정할 수 없다 - 매물번호 접두어(SEOCHO- 등)가
자치구를 가리키고 listing_repository.get_listing이 그 접두어로 CSV 파일을 찾으므로, 자치구를
옮기려면 번호 자체가 바뀌어야 해서 "수정"의 범위를 벗어난다(새로 등록해야 한다). 수정 권한(본인만,
관리자 예외 없음) 판정도 백엔드가 이미 끝낸 뒤 호출한다.
"""
from datetime import date

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.models.response_schema import ListingDetailResponse
from app.services import listing_builder, listing_repository, listing_schema as schema

router = APIRouter(prefix="/listings", tags=["listing-registration"])

MAX_DEPOSIT = 1_000_000        # 만원 (100억원)
MAX_MONTHLY_RENT = 5_000       # 만원
MAX_MAINTENANCE_FEE = 1_000    # 만원
MAX_ROOMS = 20                 # 방/욕실 수
MIN_BUILT_YEAR, MAX_BUILT_YEAR = 1900, 2035


class ListingRegistrationRequest(BaseModel):
    address_keyword: str = Field(alias="addressKeyword")  # 사용자가 입력한 도로명/지번주소 검색어
    property_type: str = Field(alias="propertyType")       # 아파트 | 오피스텔 | 연립다세대 | 단독다가구
    lease_type: str = Field(alias="leaseType")              # 전세 | 월세
    deposit: int
    monthly_rent: int = Field(0, alias="monthlyRent")       # 전세는 0
    exclusive_area: float = Field(alias="exclusiveArea")
    building_name: str = Field("", alias="buildingName")
    unit_label: str = Field("", alias="unitLabel")
    floor: str = ""
    rooms: int | None = None
    bathrooms: int | None = None
    built_year: int | None = Field(None, alias="builtYear")
    maintenance_fee: int = Field(0, alias="maintenanceFee")
    maintenance_fee_items: str = Field("", alias="maintenanceFeeItems")
    parking: str = ""
    elevator: bool | None = None
    move_in_date: str = Field("", alias="moveInDate")
    description: str = ""
    jeonse_loan_available: bool = Field(True, alias="jeonseLoanAvailable")
    photo_url: str | None = Field(None, alias="photoUrl")  # ListingPhotoController 업로드 결과 URL
    # 공인중개사 정보 (모두 선택 입력 - 더미 매물 CSV의 공인중개사_* 6컬럼, 2026-10-06 추가)
    broker_name: str = Field("", alias="brokerName")
    broker_representative: str = Field("", alias="brokerRepresentative")
    broker_reg_no: str = Field("", alias="brokerRegNo")
    broker_phone: str = Field("", alias="brokerPhone")
    broker_address: str = Field("", alias="brokerAddress")
    broker_comment: str = Field("", alias="brokerComment")

    class Config:
        populate_by_name = True


class ListingRegistrationResponse(BaseModel):
    listing_id: str = Field(alias="listingId")
    region: str
    # 저장한 CSV 행 전체(헤더 -> 값, 55컬럼). 백엔드가 DB(registered_listings.row_json)에 그대로 저장해 두었다가
    # 서버가 다시 켜질 때 돌려준다 (listing_sync.py). 프론트에는 전달되지 않는다.
    row: dict | None = None

    class Config:
        populate_by_name = True


def _extract_region(address: str) -> str | None:
    """도로명/지번주소 문자열에서 서울 25개 자치구 이름을 찾는다 (그 밖 지역은 등록 대상이 아니다)."""
    return next((gu for gu in schema.DISTRICT_ENG if gu in address), None)


def _extract_dong(jibun_address: str, region: str) -> str:
    """"서울특별시 강남구 삼성동 46-25" -> "삼성동". listing_builder.resolve_keyword는 원래 국토부
    실거래 레코드에 이미 있는 법정동을 붙이는 용도로 쓰여서 결과에 법정동을 따로 안 주므로, 지번주소
    문자열에서 역으로 뽑는다."""
    prefix = f"서울특별시 {region} "
    if not jibun_address.startswith(prefix):
        return ""
    return jibun_address[len(prefix):].strip().split(" ", 1)[0]


def _validate(request: ListingRegistrationRequest) -> None:
    if request.property_type not in {"아파트", "오피스텔", "연립다세대", "단독다가구"}:
        raise HTTPException(status_code=400, detail="매물유형이 올바르지 않습니다.")
    if request.lease_type not in {"전세", "월세"}:
        raise HTTPException(status_code=400, detail="거래유형이 올바르지 않습니다.")
    if request.deposit < 0 or request.monthly_rent < 0:
        raise HTTPException(status_code=400, detail="보증금과 월세는 0 이상이어야 합니다.")
    if request.lease_type == "전세" and request.monthly_rent != 0:
        raise HTTPException(status_code=400, detail="전세 매물은 월세를 0으로 입력해주세요.")
    if request.lease_type == "월세" and request.monthly_rent == 0:
        raise HTTPException(status_code=400, detail="월세 매물은 월세를 1만원 이상 입력해주세요.")
    if not schema.is_realistic_price(request.lease_type, request.deposit, request.monthly_rent):
        raise HTTPException(status_code=400, detail="입력한 보증금/월세가 현실적인 범위를 벗어났어요.")
    # 상식 밖 값 차단 (백엔드 ListingRegistrationRequest와 같은 기준, 2026-10-07) - 음수 관리비는 추천 카드에 음수 주거비로 나타났다.
    if not (1 <= request.exclusive_area <= 1000):  # NaN도 여기서 걸린다
        raise HTTPException(status_code=400, detail="전용면적이 올바르지 않습니다.")
    if request.deposit > MAX_DEPOSIT or request.monthly_rent > MAX_MONTHLY_RENT:
        raise HTTPException(status_code=400, detail="입력한 보증금/월세가 너무 큽니다.")
    if not (0 <= request.maintenance_fee <= MAX_MAINTENANCE_FEE):
        raise HTTPException(status_code=400, detail="관리비는 0~1000만원 사이로 입력해주세요.")
    for label, value in (("방 수", request.rooms), ("욕실 수", request.bathrooms)):
        if value is not None and not (0 <= value <= MAX_ROOMS):
            raise HTTPException(status_code=400, detail=f"{label}가 올바르지 않습니다.")
    if request.built_year is not None and not (MIN_BUILT_YEAR <= request.built_year <= MAX_BUILT_YEAR):
        raise HTTPException(status_code=400, detail="건축년도가 올바르지 않습니다.")


def _resolve_address(address_keyword: str) -> tuple[dict, str]:
    """주소 검색어 -> (resolve_keyword 결과, 자치구). 등록/수정이 똑같이 쓴다."""
    try:
        info = listing_builder.resolve_keyword(address_keyword)
    except listing_builder.Transient as e:
        raise HTTPException(status_code=502, detail=f"주소 조회에 실패했어요. 잠시 후 다시 시도해주세요: {e}") from e
    if info is None:
        raise HTTPException(status_code=400, detail="입력한 주소를 찾을 수 없어요. 도로명주소나 지번주소를 정확히 입력해주세요.")

    region = _extract_region(info["roadAddr"]) or _extract_region(info["jibunAddr"])
    if region is None:
        raise HTTPException(status_code=400, detail="서울특별시 25개 자치구 주소만 등록할 수 있어요.")
    return info, region


def _build_listing_fields(request: ListingRegistrationRequest, info: dict, region: str) -> dict:
    """등록/수정이 공통으로 채우는 필드 (registered_date/listing_status/address_source는 호출부가 따로 정한다)."""
    fields = {
        "region": region,
        "dong": _extract_dong(info["jibunAddr"], region),
        "building_name": request.building_name.strip() or info["bdNm"],  # 공백만 입력해도 주소의 건물명으로 대신한다
        "property_type": request.property_type,
        "road_address": info["roadAddr"],
        "jibun_address": info["jibunAddr"],
        "unit_label": request.unit_label,
        "floor": request.floor,
        "lat": info["lat"],
        "lon": info["lon"],
        "postal_code": info["zipNo"],
        "built_year": request.built_year,
        "lease_type": request.lease_type,
        "deposit_rent_ratio": round(request.deposit / request.monthly_rent, 1) if request.monthly_rent else None,
        "semi_jeonse_rule": "보증금÷월세 ≥ 100" if request.monthly_rent else "",
        "deposit": request.deposit,
        "monthly_rent": request.monthly_rent,
        "maintenance_fee": request.maintenance_fee,
        "maintenance_fee_items": request.maintenance_fee_items,
        "exclusive_area": request.exclusive_area,
        "rooms": request.rooms,
        "bathrooms": request.bathrooms,
        "parking": request.parking,
        "elevator": request.elevator,
        "move_in_date": request.move_in_date,
        "description": request.description,
        "jeonse_loan_available": request.jeonse_loan_available,
        "broker_name": request.broker_name,
        "broker_representative": request.broker_representative,
        "broker_reg_no": request.broker_reg_no,
        "broker_phone": request.broker_phone,
        "broker_address": request.broker_address,
        "broker_comment": request.broker_comment,
        # 주소 조회가 이미 돌려주는 행안부 주소코드 (CSV의 참고_행안부_* 4컬럼)
        "ref_bd_mgt_sn": info.get("bdMgtSn") or "",
        "ref_adm_cd": info.get("admCd") or "",
        "ref_rn_mgt_sn": info.get("rnMgtSn") or "",
        "ref_detail_dong": info.get("dongNm") or "",
    }
    if request.photo_url:
        fields["photo"] = request.photo_url
    return fields


@router.post("/register", response_model=ListingRegistrationResponse)
def register_listing(request: ListingRegistrationRequest) -> ListingRegistrationResponse:
    _validate(request)
    info, region = _resolve_address(request.address_keyword)

    listing = {
        **_build_listing_fields(request, info, region),
        "registered_date": date.today().isoformat(),
        "address_source": "회원 등록",
    }

    try:
        saved = listing_repository.append_listing(region, listing)
    except listing_repository.ListingFileLockedError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e

    return ListingRegistrationResponse(listing_id=saved["매물등록번호"], region=region, row=saved)


@router.get("/{listing_id}", response_model=ListingDetailResponse)
def get_listing_detail(listing_id: str) -> ListingDetailResponse:
    listing = listing_repository.get_listing(listing_id)
    if listing is None:
        raise HTTPException(status_code=404, detail="매물을 찾을 수 없어요.")
    return ListingDetailResponse(**listing)


@router.put("/{listing_id}", response_model=ListingRegistrationResponse)
def update_listing(listing_id: str, request: ListingRegistrationRequest) -> ListingRegistrationResponse:
    existing = listing_repository.get_listing(listing_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="매물을 찾을 수 없어요.")

    _validate(request)
    info, region = _resolve_address(request.address_keyword)
    if region != existing["region"]:
        raise HTTPException(
            status_code=400,
            detail="자치구가 바뀌는 주소로는 수정할 수 없어요. 기존 매물을 삭제하고 새로 등록해주세요.",
        )

    updates = _build_listing_fields(request, info, region)
    try:
        saved = listing_repository.update_listing(region, listing_id, updates)
    except listing_repository.ListingFileLockedError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if saved is None:
        raise HTTPException(status_code=404, detail="매물을 찾을 수 없어요.")

    return ListingRegistrationResponse(listing_id=listing_id, region=region, row=schema.listing_to_row(saved))


class ListingStatusUpdateRequest(BaseModel):
    region: str
    status: str


@router.put("/{listing_id}/status")
def update_listing_status(listing_id: str, request: ListingStatusUpdateRequest) -> dict:
    try:
        found = listing_repository.update_listing_status(request.region, listing_id, request.status)
    except listing_repository.ListingFileLockedError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    if not found:
        raise HTTPException(status_code=404, detail="매물을 찾을 수 없어요.")
    updated = listing_repository.get_listing(listing_id)
    return {"listing_id": listing_id, "status": request.status,
            "row": schema.listing_to_row(updated) if updated else None}
