"""
[담당: 송귀성] 전세자금대출 자격 판별(loan_matcher) 테스트.
실행 (customhouse-ai 폴더에서):  python -m pytest tests -q   또는   python tests/test_loan_matcher.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import loan_matcher  # noqa: E402

JEONSE = {"lease_type": "전세", "listing_deposit": 15000, "exclusive_area": 50.0}
WOLSE = {"lease_type": "월세", "listing_deposit": 1000, "exclusive_area": 30.0}


def loan(type_="GENERAL_BEOTIMMOK", lease="전세", **kw):
    body = {"type": type_, "name": f"[{type_}]", "leaseType": lease}
    body.update(kw)
    return body


def req(loans, **kw):
    base = {"annualIncome": 3400, "deposit": 800, "workLocation": "강남구", "loanProducts": loans}
    base.update(kw)
    return DiagnosisRequest(**base)


def names(request, listing=JEONSE):
    return [x["type"] for x in loan_matcher.match_eligible_loans(request, listing)]


def test_저장된_대출이_없으면_빈_목록():
    assert names(req([])) == []


def test_조건이_모두_비어_있으면_제한_없음으로_통과():
    assert names(req([loan()])) == ["GENERAL_BEOTIMMOK"]


def test_전세_대출은_전세_매물에만_월세_대출은_월세_매물에만():
    r = req([loan("A"), loan("YOUTH_MONTHLY_RENT", lease="월세")])
    assert names(r, JEONSE) == ["A"]
    assert names(r, WOLSE) == ["YOUTH_MONTHLY_RENT"]


def test_정책_대출_활용을_해제하면_대출을_보여주지_않는다():
    assert names(req([loan()], useLoanPolicy=False)) == []


def test_나이_경계와_미입력():
    r = lambda age: req([loan(minAge=19, maxAge=34)], age=age)  # noqa: E731
    assert names(r(19)) == ["GENERAL_BEOTIMMOK"]
    assert names(r(34)) == ["GENERAL_BEOTIMMOK"]
    assert names(r(18)) == [] and names(r(35)) == []
    assert names(r(None)) == ["GENERAL_BEOTIMMOK"]  # 나이 미입력이면 포함 (policy_matcher와 동일)


def test_연소득은_개인_상한과_부부합산_상한을_구분한다():
    loans = [loan(maxIncomeSingle=5000, maxIncomeCouple=7500)]
    assert names(req(loans, annualIncome=5000)) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, annualIncome=5001)) == []
    # 부부합산 소득을 입력하면 부부합산 상한(7500)으로 비교
    assert names(req(loans, annualIncome=3000, coupleAnnualIncome=7500)) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, annualIncome=3000, coupleAnnualIncome=7501)) == []
    # 부부합산 상한이 비어 있으면 개인 상한으로 비교 (본인/부부합산 중 큰 값)
    only_single = [loan(maxIncomeSingle=5000)]
    assert names(req(only_single, annualIncome=3000, coupleAnnualIncome=6000)) == []


def test_총자산_경계와_미입력():
    loans = [loan(maxAsset=33700)]
    assert names(req(loans, assets=33700)) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, assets=33701)) == []
    assert names(req(loans, assets=None)) == ["GENERAL_BEOTIMMOK"]


def test_매물_보증금_제한():
    loans = [loan(maxListingDeposit=15000)]
    assert names(req(loans), {**JEONSE, "listing_deposit": 15000}) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans), {**JEONSE, "listing_deposit": 15001}) == []


def test_최대_대출금_비율한도를_넘는_부족분은_탈락한다():
    # 매물 보증금 15000의 80% = 12000까지만 대출 가능. 보유 보증금 1000이면 부족분 14000 > 12000 -> 탈락
    loans = [loan(maxLoanRatioPercent=80)]
    assert names(req(loans, deposit=1000)) == []
    # 보유 보증금 3500이면 부족분 11500 <= 12000 -> 통과
    assert names(req(loans, deposit=3500)) == ["GENERAL_BEOTIMMOK"]


def test_최대_대출금액_절대_상한을_넘는_부족분은_탈락한다():
    loans = [loan(maxLoanAmount=10000)]
    assert names(req(loans, deposit=1000)) == []   # 부족분 14000 > 10000
    assert names(req(loans, deposit=6000)) == ["GENERAL_BEOTIMMOK"]  # 부족분 9000 <= 10000


def test_비율한도와_절대상한이_둘다_있으면_더_낮은_쪽이_적용된다():
    # 매물 보증금 15000: 비율 80% = 12000, 절대 상한 5000 -> 더 낮은 5000이 실제 한도
    loans = [loan(maxLoanRatioPercent=80, maxLoanAmount=5000)]
    assert names(req(loans, deposit=10500)) == ["GENERAL_BEOTIMMOK"]  # 부족분 4500 <= 5000
    assert names(req(loans, deposit=10000)) == ["GENERAL_BEOTIMMOK"]  # 부족분 5000 (경계, 통과)
    assert names(req(loans, deposit=9999)) == []                      # 부족분 5001 (한도 초과, 탈락)


def test_대출이_필요없으면_한도와_무관하게_통과한다():
    loans = [loan(maxLoanRatioPercent=10, maxLoanAmount=1)]
    assert names(req(loans, deposit=15000)) == ["GENERAL_BEOTIMMOK"]  # 부족분 0


def test_한도가_비어있으면_제한이_없다():
    assert names(req([loan()], deposit=0)) == ["GENERAL_BEOTIMMOK"]  # maxLoanRatioPercent/maxLoanAmount 모두 미설정


def test_전용면적_제한과_면적_정보_없는_매물():
    loans = [loan(maxExclusiveArea=85)]
    assert names(req(loans), {**JEONSE, "exclusive_area": 85.0}) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans), {**JEONSE, "exclusive_area": 85.1}) == []
    assert names(req(loans), {**JEONSE, "exclusive_area": 0}) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans), {**JEONSE, "exclusive_area": None}) == ["GENERAL_BEOTIMMOK"]


def test_필수_우대사항_자기신고_항목은_체크하지_않으면_탈락():
    loans = [loan(preferences={"NEWLYWED": {"required": True, "discount": 0.2}})]
    assert names(req(loans, preferentialStatuses=["NEWLYWED"])) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=[])) == []
    # 필수가 아니면 체크하지 않아도 통과 (우대금리 차감만 받지 못한다)
    optional = [loan(preferences={"NEWLYWED": {"required": False, "discount": 0.2}})]
    assert names(req(optional, preferentialStatuses=[])) == ["GENERAL_BEOTIMMOK"]


def test_차상위계층은_요청의_NEAR_POVERTY_코드와_연결된다():
    loans = [loan(preferences={"NEAR_POOR": {"required": True}})]
    assert names(req(loans, preferentialStatuses=["NEAR_POVERTY"])) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=["BASIC_LIVELIHOOD"])) == []


def test_필수_무주택은_명시적으로_무주택_아님일_때만_탈락():
    loans = [loan(preferences={"NO_HOME": {"required": True}})]
    assert names(req(loans, noHouseholder=True)) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, noHouseholder=None)) == ["GENERAL_BEOTIMMOK"]  # 미입력이면 포함 (policy_matcher와 동일)
    assert names(req(loans, noHouseholder=False)) == []


def test_필수_중소기업_취업청년은_직업유형_SME여야_한다():
    loans = [loan(preferences={"SME_EMPLOYED_YOUTH": {"required": True}})]
    assert names(req(loans, jobType="SME")) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, jobType="LARGE_CORP")) == []
    assert names(req(loans, jobType=None)) == []


def test_우대사항의_재반영_값이_있으면_해당_사용자에게만_공통_한도를_대체한다():
    # 매물 보증금 제한 20000인데, 신혼부부는 30000까지 재반영 - 체크한 사람만 그 한도로 심사
    loans = [loan(maxListingDeposit=20000, preferences={"NEWLYWED": {"overrideMaxListingDeposit": 30000}})]
    listing = {**JEONSE, "listing_deposit": 25000}
    assert names(req(loans, preferentialStatuses=["NEWLYWED"]), listing) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=[]), listing) == []


def test_재반영_값이_공란이면_공통_한도를_그대로_쓴다():
    loans = [loan(maxListingDeposit=20000, preferences={"NEWLYWED": {}})]
    listing = {**JEONSE, "listing_deposit": 20001}
    assert names(req(loans, preferentialStatuses=["NEWLYWED"]), listing) == []


def test_공통_한도가_제한없음이면_재반영_값과_무관하게_제한없음이다():
    loans = [loan(preferences={"NEWLYWED": {"overrideMaxListingDeposit": 10000}})]
    listing = {**JEONSE, "listing_deposit": 999999}
    assert names(req(loans, preferentialStatuses=["NEWLYWED"]), listing) == ["GENERAL_BEOTIMMOK"]


def test_연소득과_최대_대출금액도_우대사항_재반영_값을_따른다():
    loans = [loan(
        maxIncomeSingle=5000, maxLoanAmount=10000,
        preferences={"MULTI_CHILD": {"overrideMaxIncomeSingle": 8000, "overrideMaxLoanAmount": 20000}},
    )]
    r = req(loans, annualIncome=7000, preferentialStatuses=["MULTI_CHILD"], deposit=0)
    listing = {**JEONSE, "listing_deposit": 15000}  # 부족분 15000: 재반영 대출한도(20000) 안에는 들지만 공통(10000)은 넘음
    assert names(r, listing) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, annualIncome=7000, preferentialStatuses=[], deposit=0), listing) == []


def test_최대_대출금_비율한도도_우대사항_재반영_값을_따른다():
    loans = [loan(
        maxLoanRatioPercent=10,
        preferences={"NEWLYWED": {"overrideMaxLoanRatioPercent": 80}},
    )]
    listing = {**JEONSE, "listing_deposit": 15000}  # 부족분 11500: 재반영 비율한도(80% -> 12000) 안에는 들지만 공통(10% -> 1500)은 넘음
    r = req(loans, preferentialStatuses=["NEWLYWED"], deposit=3500)
    assert names(r, listing) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=[], deposit=3500), listing) == []


def test_비율한도_재반영과_절대상한_재반영이_함께_있으면_더_낮은_쪽이_적용된다():
    # 공통 조건(비율5%/절대1)은 재반영 값 유무를 가르는 "제한없음이 아님" 용도일 뿐, 신혼부부에겐 재반영 값만 쓰인다
    loans = [loan(maxLoanRatioPercent=5, maxLoanAmount=1, preferences={
        "NEWLYWED": {"overrideMaxLoanRatioPercent": 80, "overrideMaxLoanAmount": 5000},
    })]
    # 매물 보증금 15000의 재반영 비율한도 80% = 12000, 재반영 절대상한 5000 -> 더 낮은 5000이 실제 한도
    listing = {**JEONSE, "listing_deposit": 15000}
    r = req(loans, preferentialStatuses=["NEWLYWED"], deposit=10000)  # 부족분 5000 (경계, 통과)
    assert names(r, listing) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=["NEWLYWED"], deposit=9999), listing) == []  # 부족분 5001 (초과, 탈락)


def test_두_우대사항_모두_해당하면_더_관대한_재반영_값을_쓴다():
    loans = [loan(maxListingDeposit=20000, preferences={
        "NEWLYWED": {"overrideMaxListingDeposit": 25000},
        "MULTI_CHILD": {"overrideMaxListingDeposit": 30000},
    })]
    listing = {**JEONSE, "listing_deposit": 28000}
    assert names(req(loans, preferentialStatuses=["NEWLYWED", "MULTI_CHILD"]), listing) == ["GENERAL_BEOTIMMOK"]
    assert names(req(loans, preferentialStatuses=["NEWLYWED"]), listing) == []


RATE_TABLE = [
    [2.5, 2.6, 2.7],   # ~2천만원 이하
    [2.7, 2.8, 2.9],   # 2천 초과~4천 이하
    [3.0, 3.1, 3.2],   # 4천 초과~6천 이하
    [3.3, 3.4, 3.5],   # 6천 초과~7.5천 이하
]


def test_대출금리표가_있으면_소득_보증금_구간에_맞는_금리를_쓴다():
    loans = [loan(rateTable=RATE_TABLE)]
    listing = {**JEONSE, "listing_deposit": 3000, "listing_monthly_rent": 0, "maintenance_fee": 0}  # 5천 이하 구간(열 0)
    # 연소득 1500만원 -> 행 0(~2천 이하) x 열 0(5천 이하) = 2.5%
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing)[0]
    assert r["rate_percent"] == 2.5
    # 연소득 3000만원 -> 행 1(2천초과~4천이하) x 열 0 = 2.7%
    r2 = loan_matcher.match_eligible_loans(req(loans, annualIncome=3000, deposit=0), listing)[0]
    assert r2["rate_percent"] == 2.7


def test_대출금리표_임차보증금_구간은_매물_보증금_기준이다():
    loans = [loan(rateTable=RATE_TABLE)]
    # 연소득 1500만원(행 0) 고정, 매물 보증금만 구간별로 바꾼다
    cheap = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 5000})[0]
    mid = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 10000})[0]
    expensive = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 10001})[0]
    assert cheap["rate_percent"] == 2.5    # 5천만원 이하(경계 포함)
    assert mid["rate_percent"] == 2.6      # 5천 초과~1억 이하(경계 포함)
    assert expensive["rate_percent"] == 2.7  # 1억 초과


def test_대출금리표에서도_소득은_개인_부부합산_중_큰_값을_쓴다():
    loans = [loan(rateTable=RATE_TABLE)]
    listing = {**JEONSE, "listing_deposit": 3000}
    # 본인 소득은 1500(행 0)이지만 부부합산이 5000(행 2)이면 더 큰 쪽인 행 2를 쓴다
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, coupleAnnualIncome=5000, deposit=0), listing)[0]
    assert r["rate_percent"] == 3.0


def test_소득이_모든_구간을_넘으면_마지막_구간을_쓴다():
    loans = [loan(rateTable=RATE_TABLE)]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=100000, deposit=0), listing)[0]
    assert r["rate_percent"] == 3.3  # 마지막 행(6천초과~7.5천이하)


def test_대출금리표가_없으면_market_rate_percent를_기본금리로_쓴다():
    loans = [loan()]  # rateTable 없음
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 6.35
    assert r["is_temporary_rate"] is True


def test_market_rate_percent를_안_넘기면_기존_기본값을_쓴다():
    loans = [loan()]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing)[0]
    assert r["rate_percent"] == loan_matcher.DEFAULT_BASE_RATE_PERCENT


def test_대출금리표가_있으면_market_rate_percent를_무시하고_표_값을_쓴다():
    loans = [loan(rateTable=RATE_TABLE)]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 2.5  # 표 값 그대로 - market_rate_percent(6.35)는 무시된다
    assert r["is_temporary_rate"] is False


def test_기본금리가_기준금리_API_값이어도_우대금리_차감은_그대로_적용된다():
    loans = [loan(preferences={"NEWLYWED": {"discount": 0.5}})]  # rateTable 없음
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(
        req(loans, deposit=0, preferentialStatuses=["NEWLYWED"]), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 5.85


def test_대출금리표_기본금리에서도_우대금리_차감이_적용된다():
    loans = [loan(rateTable=RATE_TABLE, preferences={"NEWLYWED": {"discount": 0.5}})]
    listing = {**JEONSE, "listing_deposit": 3000}
    no_check = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0, preferentialStatuses=[]), listing)[0]
    checked = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0, preferentialStatuses=["NEWLYWED"]), listing)[0]
    assert no_check["rate_percent"] == 2.5
    assert checked["rate_percent"] == 2.0


def test_여러_대출은_각자_조건으로_따로_판별된다():
    loans = [
        loan("GENERAL_BEOTIMMOK", maxIncomeSingle=5000),
        loan("YOUTH_BEOTIMMOK", minAge=19, maxAge=34, maxIncomeSingle=5000),
        loan("NEWBORN_BEOTIMMOK", maxIncomeSingle=13000, preferences={"MULTI_CHILD": {"required": True}}),
    ]
    r = req(loans, annualIncome=3400, age=40, preferentialStatuses=[])
    assert names(r) == ["GENERAL_BEOTIMMOK"]  # 청년전용은 나이 초과, 신생아 특례는 필수 우대사항 미충족


def test_저장된_JSON의_불필요한_필드가_있어도_요청이_받아들여진다():
    body = loan(saved=True, updatedAt="2026-09-28T21:00:00", preferences=None)
    assert names(req([body])) == ["GENERAL_BEOTIMMOK"]


def test_우대사항이_없으면_기본금리_그대로_대출_부족분에_대해_실질주거비를_계산한다():
    # 보유 보증금(deposit=0)을 다 대출로 메운다고 보면 부족분 = 매물 보증금 그대로
    listing = {**JEONSE, "listing_deposit": 20000, "listing_monthly_rent": 0, "maintenance_fee": 5}
    result = loan_matcher.match_eligible_loans(req([loan()], deposit=0), listing)[0]
    assert result["rate_percent"] == loan_matcher.DEFAULT_BASE_RATE_PERCENT
    assert result["loan_principal"] == 20000
    assert result["monthly_interest"] == round(20000 * 3.0 / 100 / 12, 1)
    assert result["effective_cost"] == round(0 + 5 + result["monthly_interest"], 1)


def test_우대금리_차감은_필수여부와_무관하게_해당하면_적용된다():
    # 신혼부부는 필수가 아니지만(required=False) 해당하면 차감을 받는다
    loans = [loan(preferences={"NEWLYWED": {"required": False, "discount": 0.5}})]
    listing = {**JEONSE, "listing_deposit": 20000, "listing_monthly_rent": 0, "maintenance_fee": 0}
    no_check = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=[], deposit=0), listing)[0]
    checked = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["NEWLYWED"], deposit=0), listing)[0]
    assert no_check["rate_percent"] == 3.0
    assert checked["rate_percent"] == 2.5
    assert checked["monthly_interest"] < no_check["monthly_interest"]


def test_이자는_매물_보증금_전체가_아니라_보유_보증금을_뺀_부족분에만_계산된다():
    listing = {**JEONSE, "listing_deposit": 20000, "listing_monthly_rent": 0, "maintenance_fee": 0}
    result = loan_matcher.match_eligible_loans(req([loan()], deposit=15000), listing)[0]
    assert result["loan_principal"] == 5000  # 20000 - 15000
    assert result["monthly_interest"] == round(5000 * 3.0 / 100 / 12, 1)


def test_보유_보증금이_매물_보증금보다_많거나_같으면_대출이자가_없다():
    listing = {**JEONSE, "listing_deposit": 10000, "listing_monthly_rent": 0, "maintenance_fee": 3}
    same = loan_matcher.match_eligible_loans(req([loan()], deposit=10000), listing)[0]
    more = loan_matcher.match_eligible_loans(req([loan()], deposit=15000), listing)[0]
    for result in (same, more):
        assert result["loan_principal"] == 0
        assert result["monthly_interest"] == 0.0
        assert result["effective_cost"] == 3  # 이자 없이 월세(0) + 관리비(3)뿐


def test_여러_우대금리_차감이_합산되고_0퍼센트_밑으로는_내려가지_않는다():
    loans = [loan(preferences={
        "NEWLYWED": {"required": False, "discount": 1.0},
        "NO_HOME": {"required": False, "discount": 1.5},
        "SME_EMPLOYED_YOUTH": {"required": False, "discount": 1.0},
    })]
    r = req(loans, preferentialStatuses=["NEWLYWED"], noHouseholder=True, jobType="SME")
    result = loan_matcher.match_eligible_loans(r, JEONSE)[0]
    assert result["rate_percent"] == 0.0  # 3.0 - (1.0+1.5+1.0) = -0.5 -> 0으로 바닥


def test_보증금이_다르면_같은_대출이라도_실질주거비가_다르다():
    loans = [loan()]
    cheap = loan_matcher.match_eligible_loans(req(loans), {**JEONSE, "listing_deposit": 10000})[0]
    expensive = loan_matcher.match_eligible_loans(req(loans), {**JEONSE, "listing_deposit": 20000})[0]
    assert expensive["monthly_interest"] > cheap["monthly_interest"]
    assert expensive["effective_cost"] > cheap["effective_cost"]


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("PASS", name)
            except AssertionError:
                failed += 1
                print("FAIL", name)
                raise
    print("failures:", failed)
    sys.exit(1 if failed else 0)
