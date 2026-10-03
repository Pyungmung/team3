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


def test_이자율_미정인_대출은_저장된_금리표가_있어도_무시하고_기준금리를_쓴다():
    # 2026-10-02: 청년전용 보증부월세대출은 아직 이자율 결정 방식이 미정이라, 설령 DB에 예전 표 값이
    # 남아있어도 더는 읽지 않고(RATE_TABLE_BRACKETS_BY_LOAN_TYPE에 없음), 항상 기준금리 API 값을 "입력 대기"
    # 임시값으로 쓴다(is_temporary_rate=True). (일반/청년전용/신생아 특례 버팀목은 각자 자기 구조의 금리표를
    # 쓴다 - 아래 별도 테스트들 참고.)
    loans = [loan(type_="YOUTH_MONTHLY_RENT", lease="월세", rateTable=RATE_TABLE)]
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), WOLSE, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 6.35  # 표 값(2.5)이 아니라 기준금리를 그대로 씀
    assert r["is_temporary_rate"] is True


# 청년전용 버팀목 전세대출 금리표 - 정부 고시 그대로 연소득 4구간 x 임차보증금 단일 구간("3억원 이하").
YOUTH_RATE_TABLE = [
    [2.2],  # ~2천만원 이하
    [2.5],  # 2천 초과~4천 이하
    [2.9],  # 4천 초과~6천 이하
    [3.3],  # 6천 초과~7.5천 이하
]


def test_청년전용_버팀목_전세대출은_자기_구조의_금리표를_쓴다():
    loans = [loan(type_="YOUTH_BEOTIMMOK", rateTable=YOUTH_RATE_TABLE)]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 2.2  # 표 값(연소득 ~2천 구간) - 기준금리(6.35)는 무시
    assert r["is_temporary_rate"] is False

    r2 = loan_matcher.match_eligible_loans(req(loans, annualIncome=5000, deposit=0), listing)[0]
    assert r2["rate_percent"] == 2.9  # 연소득 4천초과~6천이하 구간


def test_청년전용_버팀목_전세대출은_임차보증금과_무관하게_단일_구간이다():
    # 임차보증금이 3억원을 훌쩍 넘어도 열이 하나뿐이라 같은 구간(index 0)을 쓴다.
    loans = [loan(type_="YOUTH_BEOTIMMOK", rateTable=YOUTH_RATE_TABLE)]
    cheap = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 3000})[0]
    expensive = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 50000})[0]
    assert cheap["rate_percent"] == expensive["rate_percent"] == 2.2


def test_청년전용_버팀목_전세대출도_금리표가_없으면_기준금리를_쓴다():
    loans = [loan(type_="YOUTH_BEOTIMMOK")]  # rateTable 없음
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, annualIncome=1500, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 6.35
    assert r["is_temporary_rate"] is True


# 신생아 특례 버팀목대출 금리표 - 연소득 9단계(1.3억원 초과부터는 맞벌이부부 전용) x 임차보증금 4단계.
NEWBORN_RATE_TABLE = [
    [1.30, 1.40, 1.50, 1.60],  # ~2천만원 이하
    [1.60, 1.70, 1.80, 1.90],  # 2천 초과~4천 이하
    [1.90, 2.00, 2.10, 2.20],  # 4천 초과~6천 이하
    [2.20, 2.30, 2.40, 2.50],  # 6천 초과~7.5천 이하
    [2.55, 2.65, 2.75, 2.85],  # 7.5천 초과~1억 이하
    [2.90, 3.00, 3.10, 3.20],  # 1억 초과~1.3억 이하
    [3.25, 3.35, 3.45, 3.55],  # (맞벌이) 1.3억 초과~1.5억 이하
    [3.60, 3.70, 3.80, 3.90],  # (맞벌이) 1.5억 초과~1.7억 이하
    [4.00, 4.10, 4.20, 4.30],  # (맞벌이) 1.7억 초과~2억 이하
]


def test_신생아_특례_버팀목대출은_9단계x4단계_자기_구조의_금리표를_쓴다():
    loans = [loan(type_="NEWBORN_BEOTIMMOK", rateTable=NEWBORN_RATE_TABLE)]
    r1 = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=1500, deposit=0), {**JEONSE, "listing_deposit": 3000}, market_rate_percent=6.35)[0]
    assert r1["rate_percent"] == 1.30  # ~2천만원 이하 x 5천만원 이하
    assert r1["is_temporary_rate"] is False

    r2 = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=3000, deposit=0), {**JEONSE, "listing_deposit": 8000})[0]
    assert r2["rate_percent"] == 1.70  # 2천초과~4천이하 x 5천초과~1억이하

    r3 = loan_matcher.match_eligible_loans(
        req(loans, annualIncome=9000, deposit=0), {**JEONSE, "listing_deposit": 20000})[0]
    assert r3["rate_percent"] == 2.85  # 7.5천초과~1억이하 x 1.5억초과


def test_신생아_특례_버팀목대출_맞벌이_아니면_연소득_1_3억_초과시_자격을_잃는다():
    # 공통 조건 연소득 한도(개인/부부합산 모두 1.3억원)를 두면, 맞벌이부부에 해당하지 않는 한 그 이상
    # 소득은 이 대출 자체가 "신청 가능"에서 빠진다(재반영 없이 공통값 그대로 적용됨). coupleAnnualIncome을
    # 보고하지 않은 개인 신고라 _income_ok는 maxIncomeSingle 쪽으로 판정한다.
    loans = [loan(type_="NEWBORN_BEOTIMMOK", maxIncomeSingle=13000, maxIncomeCouple=13000, rateTable=NEWBORN_RATE_TABLE)]
    ok = names(req(loans, annualIncome=13000, deposit=0), JEONSE)
    over = names(req(loans, annualIncome=14000, deposit=0), JEONSE)
    assert ok == ["NEWBORN_BEOTIMMOK"]
    assert over == []  # 맞벌이 체크 없이 1.3억 초과 -> 탈락


def test_신생아_특례_버팀목대출_맞벌이면_연소득_2억까지_확대되고_맞벌이전용_구간_금리가_적용된다():
    # 맞벌이부부 우대사항의 연소득(부부합산) 재반영을 2억원으로 입력해두면, 맞벌이에 해당하는 사용자는
    # 1.3억 초과(맞벌이 전용 구간)도 자격이 유지되고, 그 구간의 금리(3.25~)가 그대로 적용된다.
    loans = [loan(
        type_="NEWBORN_BEOTIMMOK", maxIncomeSingle=13000, maxIncomeCouple=13000, rateTable=NEWBORN_RATE_TABLE,
        preferences={"DUAL_INCOME": {"overrideMaxIncomeCouple": 20000}},
    )]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(
        req(loans, coupleAnnualIncome=14000, deposit=0, preferentialStatuses=["DUAL_INCOME"]), listing)[0]
    assert r["type"] == "NEWBORN_BEOTIMMOK"
    assert r["rate_percent"] == 3.25  # (맞벌이) 1.3억초과~1.5억이하 x 5천만원 이하

    too_much = names(req(loans, coupleAnnualIncome=21000, deposit=0, preferentialStatuses=["DUAL_INCOME"]), listing)
    assert too_much == []  # 맞벌이여도 재반영 한도(2억)를 넘으면 연소득 자격 자체를 잃는다


# 청년전용 보증부월세대출 전용 금리 구조 - 보증금 대출(고정금리) + 월세대출(무이자 기준액 초과분 누적 상환,
# 2년 고정 가정 - loan_matcher._RENT_LOAN_ASSUMED_TERM_MONTHS). 기간은 입력칸이 아니라 코드 고정값이다.
def _youth_monthly_rent_loan(**kw):
    defaults = dict(
        type_="YOUTH_MONTHLY_RENT", lease="월세",
        maxListingMonthlyRent=50, depositLoanRatePercent=1.3,
        monthlyRentLoanFreeThresholdManwon=20, monthlyRentLoanRatePercent=1,
    )
    defaults.update(kw)
    return loan(**defaults)


def test_청년전용_보증부월세대출은_매물_월세_제한을_넘으면_탈락한다():
    loans = [_youth_monthly_rent_loan()]
    ok = names(req(loans, deposit=0), {**WOLSE, "listing_monthly_rent": 50})
    over = names(req(loans, deposit=0), {**WOLSE, "listing_monthly_rent": 51})
    assert ok == ["YOUTH_MONTHLY_RENT"]
    assert over == []


def test_청년전용_보증부월세대출_보증금_금리는_표가_아니라_고정값이다():
    loans = [_youth_monthly_rent_loan()]
    listing = {**WOLSE, "listing_deposit": 3000, "listing_monthly_rent": 10}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 1.3  # deposit_loan_rate_percent 그대로, 기준금리(6.35) 무시
    assert r["is_temporary_rate"] is False


def test_청년전용_보증부월세대출_고정금리가_없으면_기준금리를_쓴다():
    loans = [_youth_monthly_rent_loan(depositLoanRatePercent=None)]
    listing = {**WOLSE, "listing_deposit": 3000, "listing_monthly_rent": 10}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["rate_percent"] == 6.35
    assert r["is_temporary_rate"] is True


def test_월세대출_무이자_기준액_이하면_총_이자가_0이다():
    loans = [_youth_monthly_rent_loan()]
    listing = {**WOLSE, "listing_deposit": 0, "listing_monthly_rent": 20}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing)[0]
    assert r["rent_loan_total_interest"] == 0
    assert r["rent_loan_monthly_interest"] == 0


def test_월세대출_24개월_총_이자는_누적_상환_방식으로_계산되고_월_환산값도_함께_온다():
    # 월세 50만원 - 무이자 20만원을 뺀 30만원이 매달 누적되는 구조. 1%p 기준 24개월(2년 고정 가정)
    # 총 이자 75,000원 (사용자가 직접 계산해 준 예시와 일치 - 회차별 누적액 x 1% ÷ 12를 24번 합산한 값).
    # 월 환산값(rent_loan_monthly_interest)은 그 총액을 24로 나눈 평균값(75000/24=3125원)이다.
    # 전체 한도 안내용 수치(rent_loan_total_cap_manwon)는 고정값 1200(만원)이다.
    loans = [_youth_monthly_rent_loan()]
    listing = {**WOLSE, "listing_deposit": 0, "listing_monthly_rent": 50}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing)[0]
    assert r["rent_loan_total_interest"] == 75000
    assert r["rent_loan_monthly_interest"] == 3125
    assert r["rent_loan_total_cap_manwon"] == 1200


def test_월세대출은_월_한도_없이_월세_전액이_대출_대상이다():
    # 2026-10-03: 월세대출 "월 한도" 입력칸을 없앴다 - 월세 70만원이면 70만원 전부가 대출로 충당되고(무이자 20만원 제외한
    # 50만원이 매달 누적), "대출 기간 중 최대 월세대출액 1200만원"은 안내문 전용 고정값이라 계산에 영향이 없다.
    # 매물 월세 제한(eligibility)은 이 테스트의 관심사가 아니라 넉넉히 올려둔다.
    loans = [_youth_monthly_rent_loan(maxListingMonthlyRent=100)]
    listing = {**WOLSE, "listing_deposit": 0, "listing_monthly_rent": 70}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing)[0]
    assert r["rent_loan_amount_manwon"] == 70
    assert r["rent_loan_total_interest"] == 125000  # 50만원 x 1% x (24x25/2) / 12
    assert r["rent_loan_total_cap_manwon"] == 1200


def test_다른_대출은_월세대출_총_이자가_항상_0이고_전체_한도도_None이다():
    loans = [loan(preferences={"NEWLYWED": {"discount": 0.5}})]
    listing = {**JEONSE, "listing_deposit": 3000}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0, preferentialStatuses=["NEWLYWED"]), listing)[0]
    assert r["rent_loan_total_interest"] == 0
    assert r["rent_loan_monthly_interest"] == 0
    assert r["rent_loan_total_cap_manwon"] is None
    assert r["rent_loan_amount_manwon"] == 0
    assert r["rent_loan_effective_cost"] == 0


def test_보증금이_충분해도_월세대출_대출시_실질주거비는_따로_계산된다():
    # 보유 보증금(1000)이 매물 보증금(1000)과 같아 대출이 필요 없다(loan_principal=0, monthly_interest=0).
    # 그래도 월세대출은 별개로 받을 수 있어서, rent_loan_amount_manwon/rent_loan_effective_cost는 0이 아니다.
    loans = [_youth_monthly_rent_loan()]
    listing = {**WOLSE, "listing_deposit": 1000, "listing_monthly_rent": 50, "maintenance_fee": 5}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=1000), listing)[0]
    assert r["loan_principal"] == 0
    assert r["monthly_interest"] == 0
    assert r["effective_cost"] == 55  # "월세 미대출 시" - 기존 그대로 (월세 50 + 관리비 5)
    assert r["rent_loan_amount_manwon"] == 50  # 월세 전액이 한도(50) 안에 들어와 대출로 충당
    # "월세 대출 시" = (월세 50 - 대출충당 50) + 관리비 5 + 보증금대출이자 0 + 월세대출이자 3125원(0.3125만원 환산)
    assert r["rent_loan_effective_cost"] == 5.3


def test_월세대출_대출시_실질주거비는_보증금_대출분_차감을_그대로_유지한다():
    # 보유 보증금(0)이 모자라 보증금 대출이 필요한 경우에도, "월세 대출 시" 실질주거비는 그 보증금대출
    # 이자를 그대로 더해서 계산한다(보증금 차감 로직은 바뀌지 않는다).
    loans = [_youth_monthly_rent_loan()]
    listing = {**WOLSE, "listing_deposit": 3000, "listing_monthly_rent": 50, "maintenance_fee": 5}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=0), listing, market_rate_percent=6.35)[0]
    assert r["loan_principal"] == 3000  # 매물 보증금 - 보유 보증금(0), 그대로 유지
    assert r["monthly_interest"] == round(3000 * 1.3 / 100 / 12, 1)
    assert r["rent_loan_effective_cost"] == round(0 + 5 + r["monthly_interest"] + 3125 / 10000, 1)


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


def test_여러_우대금리_차감_중_가장_큰_값_하나만_적용되고_1퍼센트_밑으로는_내려가지_않는다():
    # 2026-10-01: 여러 우대사항에 해당해도 합산하지 않고 가장 큰 차감 하나만 적용한다(모든 대출 공통).
    # YOUTH_MONTHLY_RENT는 우대금리 차감 상한(2026-10-02) 목록에 없는 대출이라 이 테스트는 상한 영향 없이
    # 순수하게 max() 동작만 검증한다 - 상한 자체는 아래 전용 테스트들에서 따로 검증한다.
    loans = [loan(type_="YOUTH_MONTHLY_RENT", lease="월세", preferences={
        "NEWLYWED": {"required": False, "discount": 1.0},
        "NO_HOME": {"required": False, "discount": 1.5},
        "SME_EMPLOYED_YOUTH": {"required": False, "discount": 1.0},
    })]
    r = req(loans, preferentialStatuses=["NEWLYWED"], noHouseholder=True, jobType="SME")
    result = loan_matcher.match_eligible_loans(r, WOLSE)[0]
    assert result["rate_percent"] == 1.5  # 3.0 - max(1.0, 1.5, 1.0) = 1.5 (합산 아님)

    # 가장 큰 차감이 기본금리를 넘으면 최종 금리 하한(연 1.0%, 2026-10-03 - 홈페이지 "1.0% 미만이면 1.0%로 적용")으로 바닥
    loans_big = [loan(type_="YOUTH_MONTHLY_RENT", lease="월세", preferences={"NO_HOME": {"required": False, "discount": 5.0}})]
    r_big = req(loans_big, noHouseholder=True)
    result_big = loan_matcher.match_eligible_loans(r_big, WOLSE)[0]
    assert result_big["rate_percent"] == 1.0
    assert result_big["rate_floor_applied"] is True


def test_우대금리_차감_상한_일반_청년전용_버팀목은_기본값_0_5퍼센트다():
    # 2026-10-02: 상한보다 작게 설정된 차감은 그대로 적용되고(상한은 천장일 뿐 바닥이 아님), 상한을 넘게
    # 설정된 차감은 상한값으로 깎인다. 다자녀가구/기초수급 등 특례 그룹이 아니면 상한은 기본값 0.5%다.
    for loan_type in ("GENERAL_BEOTIMMOK", "YOUTH_BEOTIMMOK"):
        small = [loan(type_=loan_type, preferences={"NEWLYWED": {"discount": 0.2}})]
        small_result = loan_matcher.match_eligible_loans(req(small, preferentialStatuses=["NEWLYWED"]), JEONSE)[0]
        assert small_result["rate_percent"] == 2.8  # 3.0 - 0.2 (상한 밑이라 그대로)

        big = [loan(type_=loan_type, preferences={"NEWLYWED": {"discount": 2.0}})]
        big_result = loan_matcher.match_eligible_loans(req(big, preferentialStatuses=["NEWLYWED"]), JEONSE)[0]
        assert big_result["rate_percent"] == 2.5  # 3.0 - min(2.0, 0.5) = 2.5 (상한으로 깎임)


def test_우대금리_차감_상한_다자녀가구는_0_7퍼센트다():
    for loan_type in ("GENERAL_BEOTIMMOK", "YOUTH_BEOTIMMOK"):
        loans = [loan(type_=loan_type, preferences={"MULTI_CHILD": {"discount": 2.0}})]
        result = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["MULTI_CHILD"]), JEONSE)[0]
        assert result["rate_percent"] == 2.3  # 3.0 - min(2.0, 0.7) = 2.3


def test_우대금리_차감_상한_기초수급_차상위_한부모는_1_0퍼센트다():
    for loan_type in ("GENERAL_BEOTIMMOK", "YOUTH_BEOTIMMOK"):
        for pref_key, status_code in (("BASIC_LIVELIHOOD", "BASIC_LIVELIHOOD"), ("NEAR_POOR", "NEAR_POVERTY"), ("SINGLE_PARENT", "SINGLE_PARENT")):
            loans = [loan(type_=loan_type, preferences={pref_key: {"discount": 2.0}})]
            result = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=[status_code]), JEONSE)[0]
            assert result["rate_percent"] == 2.0  # 3.0 - min(2.0, 1.0) = 2.0


def test_신생아특례_버팀목대출은_그룹과_무관하게_상한이_항상_0_5퍼센트다():
    # 다자녀가구에 해당해도(다른 대출이면 0.7%까지 가능) 신생아 특례는 예외 없이 0.5%로 고정된다.
    loans = [loan(type_="NEWBORN_BEOTIMMOK", preferences={"MULTI_CHILD": {"discount": 2.0}})]
    result = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["MULTI_CHILD"]), JEONSE)[0]
    assert result["rate_percent"] == 2.5  # 3.0 - min(2.0, 0.5) = 2.5


def test_상한_목록에_없는_대출은_우대금리_차감에_제한이_없다():
    loans = [loan(type_="YOUTH_MONTHLY_RENT", lease="월세", preferences={"NEWLYWED": {"discount": 2.0}})]
    result = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["NEWLYWED"]), WOLSE)[0]
    assert result["rate_percent"] == 1.0  # 3.0 - 2.0, 상한 없음


# 2026-10-02: 신생아 특례 버팀목대출 "전용" 우대사항 - NEWBORN_ADDITIONAL_CHILD(대출접수일 기준 2년 내 추가
# 출산한 자녀, 1명당 0.2%p)와 MINOR_CHILD_OVER_2YEARS(출생 후 2년 초과한 미성년 자녀, 1명당 0.1%p)는 입력은
# 따로 받지만(자녀 수), 최종 반영은 (discount x 인원수)를 둘이 sum()해서 "우대사항 하나"로 합친 뒤 다른
# 우대사항들과 다시 max()로 경쟁한다 - _newborn_summed_discount/_final_rate_percent 참고.
_NEWBORN_PREFS = {
    "NEWBORN_ADDITIONAL_CHILD": {"discount": 0.2},
    "MINOR_CHILD_OVER_2YEARS": {"discount": 0.1},
}


def test_신생아_추가출산_자녀_3명이면_0_2퍼센트x3명_합산후_상한에_걸린다():
    loans = [loan(type_="NEWBORN_BEOTIMMOK", preferences=_NEWBORN_PREFS)]
    result = loan_matcher.match_eligible_loans(req(loans, newbornAdditionalChildCount=3), JEONSE)[0]
    assert result["rate_percent"] == 2.5  # 3.0 - min(3*0.2, 0.5) = 3.0 - 0.5 = 2.5


def test_신생아_추가출산_1명과_2년초과_미성년_1명은_sum되어_0_3퍼센트다():
    loans = [loan(type_="NEWBORN_BEOTIMMOK", preferences=_NEWBORN_PREFS)]
    result = loan_matcher.match_eligible_loans(
        req(loans, newbornAdditionalChildCount=1, minorChildOver2YearsCount=1), JEONSE)[0]
    assert result["rate_percent"] == 2.7  # 3.0 - (1*0.2 + 1*0.1) = 2.7 (상한 0.5% 안 넘음)


def test_신생아_합산값도_다른_우대사항과_max로_경쟁한다():
    loans = [loan(type_="NEWBORN_BEOTIMMOK", preferences={**_NEWBORN_PREFS, "BASIC_LIVELIHOOD": {"discount": 2.0}})]
    result = loan_matcher.match_eligible_loans(
        req(loans, newbornAdditionalChildCount=1, preferentialStatuses=["BASIC_LIVELIHOOD"]), JEONSE)[0]
    assert result["rate_percent"] == 2.5  # max(1*0.2, 2.0)=2.0, 상한(0.5) 적용 -> 3.0-0.5=2.5


def test_신생아_우대사항_행이_없는_대출은_자녀수_입력을_무시한다():
    loans = [loan(type_="GENERAL_BEOTIMMOK", preferences={"NEWLYWED": {"discount": 0.3}})]
    result = loan_matcher.match_eligible_loans(
        req(loans, newbornAdditionalChildCount=5, preferentialStatuses=["NEWLYWED"]), JEONSE)[0]
    assert result["rate_percent"] == 2.7  # 3.0 - 0.3, 자녀수 5는 이 대출엔 의미 없어 무시됨


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


# 2026-10-03: 카드의 "금리 산출 근거 / 우대 한도 재반영" 내역
def test_금리_근거는_해당하는_우대사항별_차감과_실제_적용_하나를_보여준다():
    loans = [loan(preferences={"NEWLYWED": {"discount": 0.2}, "SINGLE_PARENT": {"discount": 0.4}, "DISABLED": {"discount": 0.1}})]
    r = loan_matcher.match_eligible_loans(
        req(loans, preferentialStatuses=["NEWLYWED", "SINGLE_PARENT"]), JEONSE, market_rate_percent=4.0
    )[0]
    assert r["base_rate_percent"] == 4.0
    assert r["discount_percent"] == 0.4 and r["rate_percent"] == 3.6
    items = {i["key"]: i for i in r["discount_items"]}
    assert set(items) == {"NEWLYWED", "SINGLE_PARENT"}  # 해당 안 하는 DISABLED는 안 나온다
    assert items["SINGLE_PARENT"]["applied"] is True and items["NEWLYWED"]["applied"] is False
    assert items["SINGLE_PARENT"]["label"] == "한부모가구"


def test_금리_근거는_상한에_걸리면_capped로_표시한다():
    loans = [loan("GENERAL_BEOTIMMOK", preferences={"NEWLYWED": {"discount": 0.9}})]
    r = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["NEWLYWED"]), JEONSE, market_rate_percent=4.0)[0]
    assert r["discount_cap_percent"] == 0.5 and r["discount_capped"] is True and r["discount_percent"] == 0.5


def test_한도_재반영_내역은_값이_실제로_바뀐_항목만_출처_우대사항과_함께_보여준다():
    loans = [loan(maxListingDeposit=30000, maxLoanAmount=20000,
                  preferences={"NEWLYWED": {"overrideMaxListingDeposit": 40000, "overrideMaxLoanAmount": 20000}})]
    r = loan_matcher.match_eligible_loans(req(loans, preferentialStatuses=["NEWLYWED"]), JEONSE)[0]
    refl = {x["field"]: x for x in r["limit_reflections"]}
    assert set(refl) == {"max_listing_deposit"}  # 최대 대출금액은 값이 같아서 제외
    assert refl["max_listing_deposit"]["base"] == 30000 and refl["max_listing_deposit"]["effective"] == 40000
    assert refl["max_listing_deposit"]["sources"] == ["신혼부부(기혼자포함)"]


def test_우대사항에_해당하지_않으면_재반영_내역이_없다():
    loans = [loan(maxListingDeposit=30000, preferences={"NEWLYWED": {"overrideMaxListingDeposit": 40000}})]
    r = loan_matcher.match_eligible_loans(req(loans), JEONSE)[0]
    assert r["limit_reflections"] == [] and r["discount_items"] == []


def test_청년전용_보증부월세대출은_고정금리_종류와_월세대출_산정_근거를_내려준다():
    loans = [loan("YOUTH_MONTHLY_RENT", lease="월세", depositLoanRatePercent=1.3,
                  monthlyRentLoanFreeThresholdManwon=20, monthlyRentLoanRatePercent=1.0)]
    listing = {**WOLSE, "listing_monthly_rent": 45, "maintenance_fee": 5}
    r = loan_matcher.match_eligible_loans(req(loans, deposit=5000), listing)[0]
    assert r["base_rate_kind"] == "fixed" and r["base_rate_percent"] == 1.3
    assert (r["rent_loan_total_cap_manwon"], r["rent_loan_free_threshold_manwon"], r["rent_loan_rate_percent"]) == (1200, 20, 1.0)
    # 다른 대출은 월세대출 근거가 없다
    other = loan_matcher.match_eligible_loans(req([loan()]), JEONSE)[0]
    assert other["rent_loan_total_cap_manwon"] is None and other["base_rate_kind"] == "market"


# 2026-10-03: 최종 금리 하한 - 홈페이지 "우대금리 적용 후 최종금리가 연 1.0% 미만인 경우에는 연 1.0%로 적용"
def test_신생아_특례_우대로_1퍼센트_밑이_되면_최종금리_1퍼센트로_올린다():
    newborn = {"type": "NEWBORN_BEOTIMMOK", "name": "신생아", "leaseType": "전세",
               "preferences": {"NEWBORN_ADDITIONAL_CHILD": {"required": True, "discount": 0.2}},
               "rateTable": [[1.3, 1.4, 1.5, 1.6]] + [[2.0] * 4] * 8}
    listing = {**JEONSE, "listing_deposit": 4000}
    r2 = loan_matcher.match_eligible_loans(req([newborn], annualIncome=1500, deposit=4000, newbornAdditionalChildCount=2), listing)[0]
    assert r2["base_rate_percent"] == 1.3 and r2["discount_percent"] == 0.4  # 1.3 - 0.4 = 0.9 -> 하한
    assert r2["rate_percent"] == 1.0 and r2["rate_floor_applied"] is True
    r1 = loan_matcher.match_eligible_loans(req([newborn], annualIncome=1500, deposit=4000, newbornAdditionalChildCount=1), listing)[0]
    assert r1["rate_percent"] == 1.1 and r1["rate_floor_applied"] is False  # 1.3 - 0.2 = 1.1 (하한 아님)


def test_우대가_없으면_기본금리가_1퍼센트_미만이어도_하한을_적용하지_않는다():
    low = {"type": "NEWBORN_BEOTIMMOK", "name": "신생아", "leaseType": "전세",
           "preferences": {}, "rateTable": [[0.8, 0.8, 0.8, 0.8]] + [[2.0] * 4] * 8}
    r = loan_matcher.match_eligible_loans(req([low], annualIncome=1500, deposit=4000), {**JEONSE, "listing_deposit": 4000})[0]
    assert r["rate_percent"] == 0.8 and r["rate_floor_applied"] is False
