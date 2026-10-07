# [담당: 송귀성] 매물 등록 입력 범위 검증 - 음수 관리비 같은 상식 밖 값이 등록돼 추천 카드에 음수 주거비로 나오던 문제 방지 (2026-10-07)
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402
from fastapi import HTTPException  # noqa: E402

from app.api.v1 import listing_registration as reg  # noqa: E402


def request(**kw):
    base = {"addressKeyword": "서울 서초구 동작대로 132", "propertyType": "오피스텔", "leaseType": "월세", "deposit": 1000, "monthlyRent": 50,
            "exclusiveArea": 23.5}
    base.update(kw)
    return reg.ListingRegistrationRequest(**base)


def test_정상_값은_통과한다():
    reg._validate(request(maintenanceFee=0, rooms=0, bathrooms=1, builtYear=2015))
    reg._validate(request(maintenanceFee=1000, rooms=20, bathrooms=20, builtYear=1900, exclusiveArea=1000))
    reg._validate(request(leaseType="전세", deposit=30000, monthlyRent=0))


def test_건물명이_공백뿐이면_주소의_건물명을_쓴다():
    info = {"roadAddr": "서울특별시 서초구 동작대로 132", "jibunAddr": "서울특별시 서초구 방배동 1-1", "bdNm": "주소건물", "zipNo": "06700", "lat": 37.5, "lon": 127.0}
    assert reg._build_listing_fields(request(buildingName="   "), info, "서초구")["building_name"] == "주소건물"
    assert reg._build_listing_fields(request(buildingName=" 내건물 "), info, "서초구")["building_name"] == "내건물"


@pytest.mark.parametrize("field,value", [
    ("maintenanceFee", -3), ("maintenanceFee", 1001), ("rooms", -1), ("rooms", 21), ("bathrooms", -1), ("bathrooms", 21),
    ("builtYear", 1899), ("builtYear", 2036), ("builtYear", 0), ("exclusiveArea", 0.5), ("exclusiveArea", 1000.1),
    ("exclusiveArea", float("nan")), ("deposit", 1_000_001), ("monthlyRent", 5001),
])
def test_상식_밖_값은_400으로_거절한다(field, value):
    with pytest.raises(HTTPException) as e:
        reg._validate(request(**{field: value}))
    assert e.value.status_code == 400


@pytest.mark.parametrize("kw", [
    {"monthlyRent": -5, "deposit": 5000},                      # 음수 월세
    {"deposit": -1},                                           # 음수 보증금
    {"leaseType": "전세", "deposit": 30000, "monthlyRent": 50},  # 전세인데 월세가 있다
    {"leaseType": "월세", "deposit": 5000, "monthlyRent": 0},    # 월세인데 월세가 0이다
])
def test_거래유형과_월세가_모순되거나_음수면_400으로_거절한다(kw):
    with pytest.raises(HTTPException) as e:
        reg._validate(request(**kw))
    assert e.value.status_code == 400
