"""
[담당: 송귀성] 더미 매물 CSV 스키마의 단일 출처 (dummyhouse_(자치구영문).csv, 55컬럼)

- 추천(listing_recommender.py)은 CSV 행을 읽어 내부 dict로 바꾸고(row_to_listing),
  신규 매물 등록(listing_repository.append_listing)과 생성 스크립트(scripts/gen_dummyhouses.py)는
  내부 dict를 CSV 행으로 바꾼다(listing_to_row). 컬럼을 바꿀 때는 이 파일의 LISTING_FIELDS만 고친다.
- 2026-09-28: 반전세 기준은 "보증금 ÷ 월세 >= 100" (월세 0인 전세는 판정하지 않는다).
"""
from datetime import date

# 현실적으로 있을 수 없는 가격 기준 (2026-09-28 사용자 지정) - 더미 매물 생성과 신규 매물 등록 검증에 같이 쓴다.
MIN_DEPOSIT = 100               # 만원. 월세/전세 모두 보증금이 이보다 작으면 비현실적
MIN_JEONSE_DEPOSIT = 3000       # 만원. 전세 보증금이 이보다 작으면 비현실적
CHEAP_WOLSE_MAX_DEPOSIT = 2000  # 만원. 월세 보증금이 이 이하이면서
CHEAP_WOLSE_MAX_RENT = 20       # 만원. 월세도 이 이하이면 비현실적 (보증금도 월세도 너무 싼 조합)
MIN_SEMI_JEONSE_RENT = 8        # 만원. 반전세(보증금 ÷ 월세 >= 100)인데 월세가 이보다 작으면 보증금과 상관없이 비현실적

PHOTO_PLACEHOLDER = "PHOTO_PLACEHOLDER"  # 사진 연결 전 임시값. 나중에 이미지 URL/경로로 바뀐다.
SEMI_JEONSE_RATIO = 100

LISTING_STATUS_AVAILABLE = "계약가능"
LISTING_STATUS_CONTRACTED = "계약중"
LISTING_STATUS_DELETED = "삭제됨"  # 회원/관리자가 삭제한 매물 - listing_recommender.py의 기존
# "계약가능이 아니면 추천 제외" 필터가 이 값도 걸러내므로 추천 로직은 따로 손볼 필요가 없다.

# 자치구 한글명 -> 파일/매물번호에 쓰는 영문명 (dummyhouse_(영문).csv, 예: 서초구 = seocho)
DISTRICT_ENG = {
    "강남구": "gangnam", "강동구": "gangdong", "강북구": "gangbuk", "강서구": "gangseo", "관악구": "gwanak",
    "광진구": "gwangjin", "구로구": "guro", "금천구": "geumcheon", "노원구": "nowon", "도봉구": "dobong",
    "동대문구": "dongdaemun", "동작구": "dongjak", "마포구": "mapo", "서대문구": "seodaemun", "서초구": "seocho",
    "성동구": "seongdong", "성북구": "seongbuk", "송파구": "songpa", "양천구": "yangcheon", "영등포구": "yeongdeungpo",
    "용산구": "yongsan", "은평구": "eunpyeong", "종로구": "jongno", "중구": "jung", "중랑구": "jungnang",
}
ENG_DISTRICT = {v: k for k, v in DISTRICT_ENG.items()}

# (CSV 헤더, 내부 키, 타입) - CSV 컬럼 순서 그대로. 타입: str / int / float / yn(Y/N -> bool)
LISTING_FIELDS: list[tuple[str, str, str]] = [
    ("매물등록번호", "listing_id", "str"),
    ("매물상태", "listing_status", "str"),
    ("등록일", "registered_date", "str"),
    ("자치구", "region", "str"),
    ("법정동", "dong", "str"),
    ("건물명", "building_name", "str"),
    ("매물유형", "property_type", "str"),
    ("도로명주소", "road_address", "str"),
    ("지번주소", "jibun_address", "str"),
    ("동호수_또는_단독층수", "unit_label", "str"),
    ("층", "floor", "str"),
    ("위도", "lat", "float"),
    ("경도", "lon", "float"),
    ("우편번호", "postal_code", "str"),
    ("건축년도", "built_year", "int"),
    ("거래유형", "lease_type", "str"),
    ("전세대출가능여부", "jeonse_loan_available", "yn"),
    ("반전세여부", "is_semi_jeonse", "yn"),
    ("보증금÷월세_비율", "deposit_rent_ratio", "float"),
    ("반전세판별기준", "semi_jeonse_rule", "str"),
    ("보증금(만원)", "deposit", "int"),
    ("월세(만원)", "monthly_rent", "int"),
    ("관리비(만원)", "maintenance_fee", "int"),
    ("관리비포함항목", "maintenance_fee_items", "str"),
    ("전용면적(㎡)", "exclusive_area", "float"),
    ("방수", "rooms", "int"),
    ("욕실수", "bathrooms", "int"),
    ("주차가능여부", "parking", "str"),
    ("엘리베이터", "elevator", "yn"),
    ("이사가능일", "move_in_date", "str"),
    ("내부사진", "photo", "str"),
    ("상세설명", "description", "str"),
    ("공인중개사_상호", "broker_name", "str"),
    ("공인중개사_대표", "broker_representative", "str"),
    ("공인중개사_등록번호", "broker_reg_no", "str"),
    ("공인중개사_전화", "broker_phone", "str"),
    ("공인중개사_주소", "broker_address", "str"),
    ("공인중개사설명", "broker_comment", "str"),
    ("참고_국토부_계약일", "ref_contract_date", "str"),
    ("참고_국토부_계약구분", "ref_contract_type", "str"),
    ("참고_국토부_계약기간", "ref_contract_term", "str"),
    ("참고_국토부_갱신요구권사용", "ref_use_rr_right", "str"),
    ("참고_국토부_종전보증금(만원)", "ref_pre_deposit", "int"),
    ("참고_국토부_종전월세(만원)", "ref_pre_monthly_rent", "int"),
    ("참고_국토부_보증금(만원)", "ref_deposit", "int"),
    ("참고_국토부_월세(만원)", "ref_monthly_rent", "int"),
    ("참고_국토부_전월세판별", "ref_lease_kind", "str"),
    ("참고_국토부_전용면적(㎡)", "ref_area", "float"),
    ("참고_국토부_층", "ref_floor", "str"),
    ("참고_국토부_지번", "ref_jibun", "str"),
    ("참고_행안부_건물관리번호", "ref_bd_mgt_sn", "str"),
    ("참고_행안부_행정구역코드", "ref_adm_cd", "str"),
    ("참고_행안부_도로명코드", "ref_rn_mgt_sn", "str"),
    ("참고_행안부_상세주소_동명", "ref_detail_dong", "str"),
    ("주소출처", "address_source", "str"),
]

LISTING_COLUMNS = [header for header, _, _ in LISTING_FIELDS]


def is_semi_jeonse(deposit: int, monthly_rent: int) -> bool:
    """월세 매물에서 보증금 ÷ 월세가 기준(100) 이상이면 반전세. 월세 0(전세)은 해당 없음."""
    return monthly_rent > 0 and deposit / monthly_rent >= SEMI_JEONSE_RATIO


def is_realistic_price(lease_type: str, deposit: int, monthly_rent: int) -> bool:
    """현실적으로 가능한 가격인지. 보증금 100만원 미만은 모두 불가, 전세는 3000만원 미만 불가,
    월세는 (보증금 2000만원 이하 and 월세 20만원 이하) 불가, 반전세(보증금/월세 >= 100)는 월세 8만원 미만 불가."""
    deposit, monthly_rent = deposit or 0, monthly_rent or 0
    if deposit < MIN_DEPOSIT:
        return False
    if lease_type == "전세":
        return deposit >= MIN_JEONSE_DEPOSIT
    if deposit <= CHEAP_WOLSE_MAX_DEPOSIT and monthly_rent <= CHEAP_WOLSE_MAX_RENT:
        return False
    return not (is_semi_jeonse(deposit, monthly_rent) and monthly_rent < MIN_SEMI_JEONSE_RENT)


def _parse(value: str, kind: str):
    text = (value or "").strip()
    if kind == "str":
        return text
    if kind == "yn":
        return text.upper() == "Y"
    if text == "":
        return None
    try:
        return int(float(text.replace(",", ""))) if kind == "int" else float(text.replace(",", ""))
    except ValueError:
        return None


def row_to_listing(row: dict) -> dict:
    """CSV 한 행(헤더 -> 문자열)을 내부 dict(영문 키, 타입 변환)로 바꾼다. 빈 숫자는 None."""
    return {key: _parse(row.get(header, ""), kind) for header, key, kind in LISTING_FIELDS}


def listing_to_row(listing: dict) -> dict:
    """내부 dict를 CSV 행(헤더 -> 값)으로 바꾼다. 없는 키는 빈 값, bool은 Y/N."""
    row = {}
    for header, key, kind in LISTING_FIELDS:
        value = listing.get(key)
        if kind == "yn":
            row[header] = "" if value is None else ("Y" if value else "N")
        else:
            row[header] = "" if value is None else value
    return row


def listing_id_prefix(region: str) -> str:
    """매물등록번호 앞부분 (예: 서초구 -> SEOCHO)."""
    return DISTRICT_ENG[region].upper()


def new_listing_id(region: str, seq: int, today: date | None = None) -> str:
    """{영문대문자}-{YYYYMM}-{4자리 일련번호}. 기존 CSV는 202609로 시작한다."""
    today = today or date.today()
    return f"{listing_id_prefix(region)}-{today:%Y%m}-{seq:04d}"
