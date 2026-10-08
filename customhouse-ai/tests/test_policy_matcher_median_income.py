"""
[담당: 송귀성] 기준중위소득(policy_matcher._median_income_100_percent_monthly_manwon/_median_income_ok) 테스트.
관리자 수정 > 기준소득관리에 저장된 값(request.income_standard, 원 단위)이 있으면 그걸 쓰고, 없으면
DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON(예전 policies.json에 있던 값, 만원 단위)으로 폴백하는지 확인한다.
실행 (customhouse-ai 폴더에서):  python tests/test_policy_matcher_median_income.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import policy_matcher  # noqa: E402

POLICY_65_PERCENT = {"median_income_percent": 65}


def req(income_standard=None, **kw):
    base = {"annualIncome": 3400, "deposit": 800, "workLocation": "강남구"}
    if income_standard is not None:
        base["incomeStandard"] = income_standard
    base.update(kw)
    return DiagnosisRequest(**base)


def test_기준소득관리에_저장된_값이_있으면_그_값을_원단위로_환산해서_쓴다():
    # 기준중위소득 100% = 2,000,000원 -> 만원 200, 65% 기준 상한 = 130만원/월
    r = req(income_standard={"medianIncome100PercentMonthly": 2_000_000}, annualIncome=130 * 12)
    assert policy_matcher._median_income_100_percent_monthly_manwon(r) == 200
    assert policy_matcher._median_income_ok(POLICY_65_PERCENT, r) is True

    over = req(income_standard={"medianIncome100PercentMonthly": 2_000_000}, annualIncome=130 * 12 + 12)
    assert policy_matcher._median_income_ok(POLICY_65_PERCENT, over) is False


def test_기준소득관리_값이_없으면_기본값으로_폴백한다():
    r = req(income_standard=None)
    legacy_manwon = policy_matcher.DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON
    assert policy_matcher._median_income_100_percent_monthly_manwon(r) == legacy_manwon


def test_기준소득관리에_중위소득값만_비어있어도_폴백한다():
    # income_standard 객체 자체는 있지만(RIR만 저장된 경우) 중위소득값이 None이면 폴백
    r = req(income_standard={"rirMetroPercent": 18.4})
    legacy_manwon = policy_matcher.DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON
    assert policy_matcher._median_income_100_percent_monthly_manwon(r) == legacy_manwon


def test_퍼센트_조건이_없는_정책은_항상_통과한다():
    r = req(income_standard={"medianIncome100PercentMonthly": 1})  # 극단적으로 낮은 값이어도
    assert policy_matcher._median_income_ok({}, r) is True


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
