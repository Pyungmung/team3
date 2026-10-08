# [담당: 송귀성] 주거정책 매칭이 요청에 실린 정책 목록(관리자 DB -> 백엔드 -> AI 엔진)으로 동작하는지, 링크/DB id가 응답까지 이어지는지,
# 조건 판별이 DB로 옮기기 전과 똑같은지 확인한다 (2026-10-08, 예전엔 CSV -> policies.json 변환 테스트였다).
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import policy_matcher  # noqa: E402


def policy(pid, name="정책", region="서울", **extra):
    """백엔드가 실어 보내는 정책 1건(camelCase) - 조건은 기본값(제한 없음)"""
    return {"id": pid, "region": region, "agency": "기관", "name": name, "description": "설명", **extra}


def request(policies, **overrides):
    base = {"annualIncome": 3400, "deposit": 800, "workLocation": "강남구", "housingPolicies": policies}
    base.update(overrides)
    return DiagnosisRequest(**base)


def names(req, region="강남구"):
    return [p["name"] for p in policy_matcher.match_display_policies(req, building_region=region)]


def test_요청에_정책이_없으면_추천도_비어_있다():
    assert policy_matcher.match_display_policies(request([]), building_region="강남구") == []
    assert DiagnosisRequest(annualIncome=1, deposit=1, workLocation="강남구", housingPolicies=None).housing_policies == []


def test_DB_id와_링크가_응답에_실리고_링크가_없으면_빈_문자열이다():
    req = request([
        policy(11, "링크 있음", link="https://example.go.kr/p1"),
        policy(12, "링크 빈칸", link=None),
        policy(13, "링크 키 없음"),
    ])
    matched = policy_matcher.match_display_policies(req, building_region="강남구")
    assert [(m["id"], m["link"]) for m in matched] == [(11, "https://example.go.kr/p1"), (12, ""), (13, "")]


def test_입력_순서가_표시_순서다():
    req = request([policy(3, "다"), policy(1, "가"), policy(2, "나")])
    assert names(req) == ["다", "가", "나"]


def test_지역은_서울_공통이거나_그_자치구와_같아야_한다():
    req = request([policy(1, "공통", "서울"), policy(2, "강남", "강남구"), policy(3, "서초", "서초구")])
    assert names(req, "강남구") == ["공통", "강남"]
    assert names(req, "서초구") == ["공통", "서초"]
    assert names(req, "마포구") == ["공통"]


def test_나이_소득_자산_조건은_이하_이상_경계값을_포함한다():
    req = request([
        policy(1, "나이", minAge=19, maxAge=34),
        policy(2, "소득", maxAnnualIncome=3400),
        policy(3, "자산", maxAsset=5000),
    ], age=34, assets=5000)
    assert names(req) == ["나이", "소득", "자산"]
    assert names(request([policy(1, "나이", minAge=19, maxAge=34)], age=35)) == []
    assert names(request([policy(1, "나이", minAge=19, maxAge=34)], age=18)) == []
    assert names(request([policy(2, "소득", maxAnnualIncome=3400)], annualIncome=3401)) == []
    assert names(request([policy(2, "소득", maxAnnualIncome=3400)], annualIncome=3000, coupleAnnualIncome=3500)) == []   # 부부합산이 더 크면 그 값으로 심사
    assert names(request([policy(3, "자산", maxAsset=5000)], assets=5001)) == []


def test_나이_자산을_입력하지_않으면_일단_후보에_포함한다():
    req = request([policy(1, "나이", minAge=19, maxAge=34), policy(2, "자산", maxAsset=5000)])
    assert names(req) == ["나이", "자산"]


def test_무주택은_아니라고_답한_경우에만_탈락하고_모르면_포함한다():
    pol = [policy(1, "무주택", requireNoHousehold=True)]
    assert names(request(pol, noHouseholder=None)) == ["무주택"]
    assert names(request(pol, noHouseholder=True)) == ["무주택"]
    assert names(request(pol, noHouseholder=False)) == []


def test_기초수급_중소기업_신혼부부는_체크하지_않으면_탈락한다():
    pol = [policy(1, "수급", requireBasicLivelihood=True), policy(2, "중소", requireSme=True), policy(3, "신혼", requireNewlywed=True)]
    assert names(request(pol)) == []
    assert names(request(pol, preferentialStatuses=["BASIC_LIVELIHOOD", "NEWLYWED"], jobType="SME")) == ["수급", "중소", "신혼"]


def test_대출_상품은_정책_대출_활용을_끄면_숨긴다():
    pol = [policy(1, "대출", loan=True), policy(2, "지원금")]
    assert names(request(pol, useLoanPolicy=True)) == ["대출", "지원금"]
    assert names(request(pol, useLoanPolicy=False)) == ["지원금"]


def test_기준중위소득_퍼센트는_기준소득관리_값이_있으면_그_값으로_없으면_기본값으로_계산한다():
    pol = [policy(1, "중위", medianIncomePercent=65)]
    # 기준소득관리 값(원): 기준중위소득 100% = 2,000,000원 -> 65% = 130만원/월 = 연 1560만원
    custom = {"medianIncome100PercentMonthly": 2_000_000}
    assert names(request(pol, incomeStandard=custom, annualIncome=1560)) == ["중위"]
    assert names(request(pol, incomeStandard=custom, annualIncome=1572)) == []
    # 값이 없으면 기본값 256.4238만원 -> 65% = 166.68만원/월 = 연 2000.2만원
    assert names(request(pol, annualIncome=2000)) == ["중위"]
    assert names(request(pol, annualIncome=2010)) == []
