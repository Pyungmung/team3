"""
[담당: 송귀성] 전세자금대출 자격 판별 + 실질주거비 계산 (관리자 수정 > 전세자금대출에서 저장한 조건 <-> 사용자 조건·매물)
관리자 화면에서 입력한 대출 조건(나이/연소득/총자산/매물 보증금/전용면적/필수 우대사항/대출 한도)으로, 추천된 매물마다
"이 대출을 신청할 수 있는지"를 판별하고, 신청 가능하면 "[대출이름] 실질주거비"를 계산해 리포트 카드에 보여준다.

2026-09-29: 최대 대출금 비율한도(%, 매물 보증금 기준)와 최대 대출금액(절대 상한)을 추가했다. 대출로 메워야 하는
부족분(매물 보증금 - 보유 보증금)이 이 한도(둘 다 있으면 더 낮은 쪽)를 넘으면, 대출로도 그 매물을 감당할 수
없으므로 "신청 가능"에서 뺀다 - _loan_amount_ok/_max_loan_available 참고.

2026-09-30: 신혼부부/다자녀가구처럼 일부 우대사항은 매물 보증금 제한/연소득/최대 대출금액/최대 대출금 비율한도/
전용면적을 공통 조건과 다른(항상 더 관대한 쪽으로만 - 여러 우대사항이 겹치면 더 큰 값이 이긴다) 자체 한도로
심사한다. 관리자가 그 우대사항 행에 재반영 값을 입력해두면, 사용자가 그 우대사항에 해당할 때 공통 조건 대신 그
값으로 자격을 판별한다(공란이면 공통 조건 그대로) - _effective_limit 참고. "더 관대한 쪽이 이긴다"는 방향이
고정이라, 청년전용 버팀목 전세대출처럼 특정 나이 미만에서 한도를 "좁혀야" 하는 경우는 반대로 공통 조건 자체를
그 좁은 기준값으로 두고, "만 25세 이상" 우대사항이 재반영으로 넓혀주는 식으로 설계한다(2026-10-01).

2026-09-30: 관리자가 대출별 실제 금리표(부부합산 연소득 4구간 x 임차보증금 3구간, 정부 고시 표 그대로)를
입력해두면 기본금리를 그 표에서 찾아 쓴다(RATE_TABLE_INCOME_BRACKETS_MANWON/RATE_TABLE_DEPOSIT_BRACKETS_MANWON,
_table_base_rate_percent 참고). 소득 구간은 개인/부부합산 구분 없이 policy_matcher와 같은 "더 큰 쪽"
기준이다(실제 버팀목대출 심사도 배우자가 없으면 본인 소득을 그대로 쓴다).

2026-09-30: 아직 표를 안 넣은 대출의 기본금리는, 예전엔 임시 고정값 3%였지만 이제 "보증금액 전환 이자기회비용"과
같은 기준금리 API 값(한국부동산원 수도권 전월세 전환율, reb_conversion_rate.py)을 그대로 쓴다 - match_eligible_loans의
market_rate_percent 인자로 호출하는 쪽(listing_recommender.py)이 넘겨준다. DEFAULT_BASE_RATE_PERCENT는 그 인자를
안 넘긴 호출(주로 테스트)에서만 쓰는 기본값이다.

판별 방식은 주거정책 추천(policy_matcher.py)과 같은 규칙이다 - 나이/자산/무주택은 미입력이면 일단 포함(관대),
자기신고 항목(기초수급 등)과 직업 유형은 필수로 요구하는데 체크하지 않았으면 탈락(보수적). 가능한 조건 함수는
policy_matcher의 것을 그대로 재사용한다.

필수(required) 우대사항과 우대금리 차감(discount)은 서로 다른 조건식이다:
  - required: 대출 "신청 가능 여부"(자격)를 가른다 - 체크된 우대사항이 필수인데 사용자가 해당하지 않으면 그 대출 자체가 안 보인다.
  - discount: 자격과 무관하게, 사용자가 그 우대사항에 해당하면(필수든 아니든) 최종 금리에서 깎아주는 값이다.
대출 조건은 백엔드가 DB에서 읽어 요청(request.loan_products)에 실어 보낸다 - 이 모듈은 DB나 파일을 읽지 않는다.
"""
from app.services import policy_matcher

# match_eligible_loans에 market_rate_percent를 안 넘긴 호출(주로 테스트)에서만 쓰는 기본값 (연 %).
# 실제 서비스 호출(listing_recommender.py)은 항상 기준금리 API 값(reb_conversion_rate)을 명시적으로 넘긴다.
DEFAULT_BASE_RATE_PERCENT = 3.0

# 대출금리표의 구간 정의 (관리자 화면의 표와 순서가 같아야 한다 - admin/loans.html 참고).
# 소득: 상한(만원) 오름차순, 이 값 이하면 그 구간 - 마지막 구간(7500)을 넘으면 그냥 마지막 구간을 쓴다
# (실제로는 그 전에 max_income_couple/single 자격 조건에서 이미 걸러지는 게 보통이다).
RATE_TABLE_INCOME_BRACKETS_MANWON = [2000, 4000, 6000, 7500]
# 임차보증금: 상한(만원), 마지막(None)은 상한 없음(1억원 초과 전부).
RATE_TABLE_DEPOSIT_BRACKETS_MANWON = [5000, 10000, None]

# 관리자 화면의 우대사항 코드 -> 요청의 preferential_statuses 코드. 이름이 같으면 생략한다.
# (관리자 화면은 NEAR_POOR, 주거조건 입력은 NEAR_POVERTY로 코드가 달라서 여기서 맞춘다)
_STATUS_CODE_BY_PREFERENCE = {"NEAR_POOR": "NEAR_POVERTY"}
# DUAL_INCOME(맞벌이부부)/ONE_CHILD(1자녀)/TWO_CHILDREN(2자녀)/MULTI_CHILD(다자녀가구)는 관리자 화면에서
# "부가 우대사항"으로 시각적으로만 묶여 보일 뿐, 여기서는 다른 자기신고 항목과 완전히 동일하게 독립적으로 판별한다.
_SELF_REPORTED_PREFERENCES = (
    "BASIC_LIVELIHOOD", "NEAR_POOR", "SINGLE_PARENT", "INDEPENDENT_YOUTH", "NEWLYWED",
    "DUAL_INCOME", "ONE_CHILD", "TWO_CHILDREN", "MULTI_CHILD",
    "DISABLED", "MULTICULTURAL", "ELDERLY_DEPENDENT", "ELDERLY_HOUSEHOLD",
)


def _preference_satisfied(key: str, request) -> bool:
    """사용자가 이 우대사항 조건에 해당하는지 (required 여부와 무관 - 필수 판별과 우대금리 차감이 공용으로 쓴다).
    - 자기신고 항목(기초수급/차상위/한부모/자립준비청년/신혼부부/다자녀): 체크했어야 해당
    - 중소기업 취업청년: 주거조건의 직업 유형이 SME(중소기업)여야 해당
    - 무주택: 명시적으로 "무주택 아님"이 아니면 해당 (policy_matcher._no_household_ok와 같은 관대한 처리)
    - 만 25세 미만/이상(AGE_UNDER_25/AGE_25_OR_OLDER): 둘 다 체크박스가 아니라 진단 폼의 "만 나이"로 자동
      판별 - 나이/자산처럼 관대하게 처리하지 않고, 나이를 입력 안 했으면(None) 둘 다 해당 없음으로 본다(혜택을
      주는 조건이라 보수적으로 처리). AGE_25_OR_OLDER가 "미만"이 아니라 "이상"을 조건으로 잡은 이유는
      _effective_limit의 "여러 우대사항이 해당하면 더 관대한 값이 이긴다" 방향과 맞추기 위해서다 - 청년전용
      버팀목 전세대출은 공통 조건 자체를 25세 미만 기준값(더 좁은 쪽)으로 두고, 25세 이상이면 이 우대사항
      재반영으로 넓혀주는 식으로 쓴다(관리자 화면 참고). AGE_UNDER_25는 그 반대쪽(25세 미만 그룹) 전용
      우대금리/재반영을 따로 주고 싶을 때 쓴다."""
    if key in _SELF_REPORTED_PREFERENCES:
        return _STATUS_CODE_BY_PREFERENCE.get(key, key) in request.preferential_statuses
    if key == "SME_EMPLOYED_YOUTH":
        return request.job_type == "SME"
    if key == "NO_HOME":
        return policy_matcher._no_household_ok({"require_no_household": True}, request.no_householder)
    if key == "AGE_25_OR_OLDER":
        return request.age is not None and request.age >= 25
    if key == "AGE_UNDER_25":
        return request.age is not None and request.age < 25
    if key in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD:
        return _newborn_child_count(key, request) > 0
    return False  # 관리자 화면에 없는 낯선 코드는 해당 없음으로 본다


# 신생아 특례 버팀목대출 "전용" 우대사항 - 체크박스가 아니라 자녀 "수"(진단 폼의 숫자 입력)로 판별하고,
# discount는 "1명당 차감율(%p)"로 쓴다(최종 차감 = discount x 인원수). 2026-10-02: 이 둘은 서로 다른
# 우대사항이라 각자 입력은 따로 받지만, 최종 우대금리 계산에서는 둘을 sum()해서 "우대사항 하나"로 합친 뒤
# 다른 우대사항들과 다시 max()로 비교한다(가장 유리한 쪽 하나만 적용) - _final_rate_percent 참고.
_NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD = {
    "NEWBORN_ADDITIONAL_CHILD": "newborn_additional_child_count",
    "MINOR_CHILD_OVER_2YEARS": "minor_child_over_2years_count",
}


def _newborn_child_count(key: str, request) -> int:
    return getattr(request, _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD[key], None) or 0


def _newborn_summed_discount(loan, request) -> float:
    """NEWBORN_ADDITIONAL_CHILD/MINOR_CHILD_OVER_2YEARS 두 우대사항의 (1명당 차감율 x 인원수)를 더한 값.
    대출에 그 우대사항 행이 아예 없으면(다른 대출들) 0으로, 있어도 인원수가 0이면 0이다."""
    total = 0.0
    for key, count_field in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD.items():
        setting = loan.preferences.get(key)
        if setting is None:
            continue
        total += (setting.discount or 0.0) * _newborn_child_count(key, request)
    return total


def _effective_limit(loan, request, base_limit, override_getter):
    """base_limit(공통 조건 한도)을, 사용자가 해당하는 우대사항 중 재반영 값이 설정된 게 있으면 그 값으로 바꾼다.
    여러 우대사항에 해당하고 재반영 값이 서로 다르면 더 관대한(높은) 값을 쓴다. base_limit이 None(제한 없음)이면
    이미 제한이 없으므로 재반영 값과 무관하게 그대로 None이다."""
    if base_limit is None:
        return None
    overrides = [
        override_getter(setting)
        for key, setting in loan.preferences.items()
        if override_getter(setting) is not None and _preference_satisfied(key, request)
    ]
    return max(overrides) if overrides else base_limit


def _effective_income(request) -> int:
    """심사에 쓰는 연소득 = max(본인, 부부합산) (policy_matcher._annual_income_ok와 같다).
    배우자가 없으면 본인 연소득을 그대로 쓴다 - 대출금리표 구간 판정에도 이 값을 그대로 쓴다(_table_base_rate_percent)."""
    return max(request.annual_income, request.couple_annual_income or 0)


def _bracket_index(value: float, upper_bounds: list) -> int:
    """오름차순 상한 목록에서 value가 속하는 구간의 인덱스. 마지막 상한은 None이면 무제한(항상 그 구간).
    value가 모든 상한을 넘으면(마지막이 None이 아닌데 넘는 경우) 마지막 구간으로 묶는다(정책 조건에서 이미
    걸러지는 게 보통이라 여기서 탈락시키지 않고 가장 가까운 구간으로 처리한다)."""
    for i, upper in enumerate(upper_bounds):
        if upper is None or value <= upper:
            return i
    return len(upper_bounds) - 1


def _table_base_rate_percent(loan, request, listing: dict) -> float | None:
    """대출금리표(loan.rate_table)에서 연소득 구간(행) x 임차보증금 구간(열)에 해당하는 기본금리를 찾는다.
    표가 없으면 None(호출하는 쪽이 DEFAULT_BASE_RATE_PERCENT로 대신한다)."""
    if not loan.rate_table:
        return None
    income_idx = _bracket_index(_effective_income(request), RATE_TABLE_INCOME_BRACKETS_MANWON)
    deposit_idx = _bracket_index(listing.get("listing_deposit") or 0, RATE_TABLE_DEPOSIT_BRACKETS_MANWON)
    return loan.rate_table[income_idx][deposit_idx]


def _income_ok(loan, request) -> bool:
    """연소득(이하): 판정 소득 = max(본인, 부부합산) (policy_matcher._annual_income_ok와 같다).
    부부합산 소득을 입력했고 부부합산 상한이 있으면 그 상한으로, 아니면 개인 상한으로 비교한다."""
    income = _effective_income(request)
    max_income_couple = _effective_limit(loan, request, loan.max_income_couple, lambda s: s.override_max_income_couple)
    max_income_single = _effective_limit(loan, request, loan.max_income_single, lambda s: s.override_max_income_single)
    limit = max_income_couple if (request.couple_annual_income and max_income_couple is not None) else max_income_single
    return limit is None or income <= limit


def _listing_deposit_ok(loan, request, listing: dict) -> bool:
    limit = _effective_limit(loan, request, loan.max_listing_deposit, lambda s: s.override_max_listing_deposit)
    return limit is None or (listing.get("listing_deposit") or 0) <= limit


def _area_ok(loan, request, listing: dict) -> bool:
    """전용면적(이하): 면적 정보가 없는 매물(0/None)은 일단 포함. 청년전용 버팀목 전세대출처럼 공통 조건
    자체를 더 좁은 기준값으로 두고, 특정 우대사항(예: 만 25세 이상)이 재반영으로 더 넓혀줄 수 있다
    (_effective_limit, "더 관대한 값이 이긴다" 방향과 일치)."""
    limit = _effective_limit(loan, request, loan.max_exclusive_area, lambda s: s.override_max_exclusive_area)
    area = listing.get("exclusive_area")
    return limit is None or not area or area <= limit


def _required_preferences_ok(loan, request) -> bool:
    """필수(required)로 표시된 우대사항을 모두 충족해야 한다 (하나라도 required인데 해당 안 하면 탈락)."""
    return all(_preference_satisfied(key, request) for key, setting in loan.preferences.items() if setting.required)


def _max_loan_available(loan, request, listing: dict) -> float | None:
    """이 대출로 이 매물에 실제 빌릴 수 있는 최대 금액(만원) = 매물 보증금의 비율한도, 절대 상한 중 더 낮은 쪽
    (관리자가 최대 대출금 비율한도(%)와 최대 대출금액을 둘 다 넣으면 더 작은 쪽이 실제 한도다). 둘 다 비어 있으면 제한 없음(None).
    비율한도/절대 상한 모두 신혼부부/다자녀가구 재반영 값이 있으면 그 값으로 바뀐다(_effective_limit)."""
    limits = []
    max_loan_ratio_percent = _effective_limit(loan, request, loan.max_loan_ratio_percent, lambda s: s.override_max_loan_ratio_percent)
    if max_loan_ratio_percent is not None:
        limits.append((listing.get("listing_deposit") or 0) * max_loan_ratio_percent / 100)
    max_loan_amount = _effective_limit(loan, request, loan.max_loan_amount, lambda s: s.override_max_loan_amount)
    if max_loan_amount is not None:
        limits.append(max_loan_amount)
    return min(limits) if limits else None


def _loan_amount_ok(loan, request, listing: dict) -> bool:
    """대출로 메워야 하는 부족분(매물 보증금 - 보유 보증금)이 이 대출의 최대 대출 가능액을 넘으면 이 대출로는
    이 매물을 감당할 수 없으므로 탈락한다. 부족분이 0이면(이미 대출 없이 충분하면) 한도와 무관하게 통과한다."""
    shortfall = max(0, (listing.get("listing_deposit") or 0) - (request.deposit or 0))
    if shortfall == 0:
        return True
    available = _max_loan_available(loan, request, listing)
    return available is None or shortfall <= available


def is_eligible(loan, request, listing: dict) -> bool:
    """대출 1종이 매물 1건에 적용되는지. 대상 매물 유형(전세/월세)까지 맞아야 한다."""
    if loan.lease_type != listing.get("lease_type"):
        return False
    return (
        policy_matcher._age_ok({"min_age": loan.min_age, "max_age": loan.max_age}, request.age)
        and _income_ok(loan, request)
        and policy_matcher._asset_ok({"max_asset": loan.max_asset}, request.assets)
        and _listing_deposit_ok(loan, request, listing)
        and _area_ok(loan, request, listing)
        and _required_preferences_ok(loan, request)
        and _loan_amount_ok(loan, request, listing)
    )


# 2026-10-02: 관리자 요청으로 우대금리 차감에 대출별 상한(cap)을 둔다 - 위에서 고른 "가장 큰 차감값"이라도
# 이 상한을 넘으면 상한으로 깎인다(차감이 상한보다 작으면 그대로 - 상한은 바닥이 아니라 천장이다). 일반/청년전용
# 버팀목 전세대출은 그룹별로 상한이 다르고(기초생활수급자·차상위계층·한부모가구가 가장 넓고, 다자녀가구가 그
# 다음, 나머지는 기본값), 신생아 특례 버팀목대출은 예외 없이 기본값 하나다. 목록에 없는 대출(예: 청년전용
# 보증부월세대출)은 상한 없이 기존처럼 무제한이다.
_DISCOUNT_CAP_DEFAULT_PERCENT = 0.5
_DISCOUNT_CAP_MULTI_CHILD_PERCENT = 0.7
_DISCOUNT_CAP_HIGH_TIER_PERCENT = 1.0
_DISCOUNT_CAP_HIGH_TIER_PREFERENCES = ("BASIC_LIVELIHOOD", "NEAR_POOR", "SINGLE_PARENT")
_LOAN_TYPES_WITH_TIERED_DISCOUNT_CAP = ("GENERAL_BEOTIMMOK", "YOUTH_BEOTIMMOK")
_LOAN_TYPES_WITH_FLAT_DISCOUNT_CAP = ("NEWBORN_BEOTIMMOK",)


def _discount_cap_percent(loan, request) -> float | None:
    """이 대출·사용자 조합에서 우대금리 차감이 넘을 수 없는 상한(%p). None이면 상한 없음(무제한)."""
    if loan.type in _LOAN_TYPES_WITH_FLAT_DISCOUNT_CAP:
        return _DISCOUNT_CAP_DEFAULT_PERCENT
    if loan.type in _LOAN_TYPES_WITH_TIERED_DISCOUNT_CAP:
        if any(_preference_satisfied(key, request) for key in _DISCOUNT_CAP_HIGH_TIER_PREFERENCES):
            return _DISCOUNT_CAP_HIGH_TIER_PERCENT
        if _preference_satisfied("MULTI_CHILD", request):
            return _DISCOUNT_CAP_MULTI_CHILD_PERCENT
        return _DISCOUNT_CAP_DEFAULT_PERCENT
    return None


def _final_rate_percent(loan, request, listing: dict, market_rate_percent: float) -> float:
    """기본금리(대출금리표가 있으면 그 표의 값, 없으면 market_rate_percent - 기준금리 API 값)에서, 사용자가
    해당하는 우대사항 중 "가장 큰 우대금리 차감 하나만" 뺀 최종 금리(연 %) - 여러 우대사항에 해당해도 중복으로
    합산하지 않는다(2026-10-01, 관리자 요청으로 sum()에서 max()로 변경. 모든 대출 공통). required 여부와
    무관하게 "해당하면" 차감 후보가 된다(필수 자격 판별과는 별개 계산식). 단, NEWBORN_ADDITIONAL_CHILD/
    MINOR_CHILD_OVER_2YEARS 둘은 예외로 서로 sum()해서 "우대사항 하나"의 후보값으로 만든 뒤에 이 max() 풀에
    섞는다(_newborn_summed_discount, 2026-10-02) - 입력은 따로 받지만 최종 반영은 합쳐서 하나로 경쟁시킨다.
    그 값이 대출별 상한(cap)을 넘으면 상한으로 깎인다(_discount_cap_percent 참고, 2026-10-02). 0% 밑으로는
    내려가지 않는다."""
    base_rate = _table_base_rate_percent(loan, request, listing)
    if base_rate is None:
        base_rate = market_rate_percent
    candidates = [
        setting.discount or 0.0
        for key, setting in loan.preferences.items()
        if key not in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD and _preference_satisfied(key, request)
    ]
    candidates.append(_newborn_summed_discount(loan, request))
    discount = max(candidates, default=0.0)
    cap = _discount_cap_percent(loan, request)
    if cap is not None:
        discount = min(discount, cap)
    return round(max(0.0, base_rate - discount), 2)


def _loan_result(loan, request, listing: dict, market_rate_percent: float) -> dict:
    """이 대출을 신청했을 때의 "[대출이름] 실질주거비". 보증금전환 실질거주비와 같은 방식(월세+관리비+이자)이되,
    이자는 매물 보증금 전체가 아니라 "매물 보증금 - 사용자가 지금 가진 보증금(request.deposit)"만큼, 즉 대출로
    메워야 하는 부족분에 대해서만 계산한다(자기 돈으로 낼 수 있는 만큼은 대출이 필요 없다).
    보유 보증금이 매물 보증금과 같거나 더 많으면(대출이 필요 없으면) 부족분이 0이라 이자도 0이 된다."""
    rate = _final_rate_percent(loan, request, listing, market_rate_percent)
    listing_deposit = listing.get("listing_deposit") or 0
    loan_principal = max(0, listing_deposit - (request.deposit or 0))
    monthly_interest = round(loan_principal * rate / 100 / 12, 1)
    rent = listing.get("listing_monthly_rent") or 0
    maintenance = listing.get("maintenance_fee") or 0
    return {
        "type": loan.type,
        "name": loan.name,
        "rate_percent": rate,
        "is_temporary_rate": not loan.rate_table,  # True면 이 대출에 아직 실제 금리표가 없어 기준금리 API 값을 대신 쓴 것
        "loan_principal": loan_principal,  # 대출로 메우는 부족분 (매물 보증금 - 보유 보증금, 0 이상)
        "monthly_interest": monthly_interest,
        "effective_cost": round(rent + maintenance + monthly_interest, 1),
    }


def match_eligible_loans(request, listing: dict, market_rate_percent: float = DEFAULT_BASE_RATE_PERCENT) -> list[dict]:
    """매물 1건에 신청 가능한 대출 목록(각각 실질주거비 포함). 저장된 대출이 없거나 "정책 대출 활용"을 해제했으면
    빈 목록이다 (policy_matcher가 대출 상품을 추천하지 않는 것과 같다).
    market_rate_percent: 대출금리표가 없는 대출의 기본금리로 쓰는 기준금리 API 값(연 %) - 호출하는 쪽
    (listing_recommender.py)이 "보증금액 전환 이자기회비용"과 같은 reb_conversion_rate 값을 넘겨준다."""
    if not request.loan_products or not request.use_loan_policy:
        return []
    return [
        _loan_result(loan, request, listing, market_rate_percent)
        for loan in request.loan_products if is_eligible(loan, request, listing)
    ]
