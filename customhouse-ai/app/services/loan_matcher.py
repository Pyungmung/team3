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
# 소득: 상한(만원) 오름차순, 이 값 이하면 그 구간 - 마지막 구간을 넘으면 그냥 마지막 구간을 쓴다(실제로는
# 그 전에 max_income_couple/single 자격 조건에서 이미 걸러지는 게 보통이다). 일반/청년전용 버팀목 공통 구간.
RATE_TABLE_INCOME_BRACKETS_MANWON = [2000, 4000, 6000, 7500]
# 임차보증금: 상한(만원), 마지막(None)은 상한 없음(1억원 초과 전부). 일반 버팀목 전세대출의 구간이다.
RATE_TABLE_DEPOSIT_BRACKETS_MANWON = [5000, 10000, None]

# 2026-10-02: 대출금리표(정부 고시 연소득x임차보증금 구간표)는 대출마다 구간 구조가 전부 다를 수 있다 -
# 일반 버팀목 전세대출은 연소득 4단계 x 임차보증금 5천/1억 기준 3단계, 청년전용 버팀목 전세대출은 같은
# 연소득 4단계에 임차보증금은 정부 고시 그대로 "3억원 이하" 단일 구간(열이 하나뿐이라 경계값 자체는
# 의미 없다), 신생아 특례 버팀목대출은 연소득 9단계(1.3억원 초과부터는 맞벌이부부 우대사항에 해당해야만
# 적용되는 구간 - 공통 조건 연소득 한도 자체를 1.3억원으로 두고 맞벌이부부 우대사항의 연소득 재반영을
# 2억원으로 입력해두면 이 구간까지 자격이 열린다, admin/loans.html의 안내 참고) x 임차보증금 4단계다.
# 이 목록에 없는 대출(청년전용 보증부월세대출)은 아직 이자율 결정 방식이 미정이라, 설령 DB에 예전 표 값이
# 남아있어도 더는 읽지 않는다(관리자 화면도 이 목록에 있는 대출에서만 표를 보여준다). 미정인 대출은 기준금리
# API 값을 "입력 대기" 임시값으로 쓴다(is_temporary_rate=True로 표시).
RATE_TABLE_BRACKETS_BY_LOAN_TYPE = {
    "GENERAL_BEOTIMMOK": {
        "income": RATE_TABLE_INCOME_BRACKETS_MANWON,
        "deposit": RATE_TABLE_DEPOSIT_BRACKETS_MANWON,
    },
    "YOUTH_BEOTIMMOK": {
        "income": RATE_TABLE_INCOME_BRACKETS_MANWON,
        "deposit": [30000],  # [3억원 이하] 단일 구간
    },
    "NEWBORN_BEOTIMMOK": {
        # 9단계: ~2천/4천/6천/7.5천/1억/1.3억(공통 조건 연소득 한도) / (맞벌이) 1.5억/1.7억/2억
        "income": [2000, 4000, 6000, 7500, 10000, 13000, 15000, 17000, 20000],
        "deposit": [5000, 10000, 15000, None],  # 5천/1억/1.5억 기준 4단계
    },
}

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


def _discountable_child_count(key: str, request) -> int:
    """우대금리 차감에 실제로 곱해지는 인원수. NEWBORN_ADDITIONAL_CHILD(대출접수일 기준 2년 내 출산한 자녀)는 첫 번째 자녀는
    차감이 없고 두 번째부터 1명씩 세므로 (입력한 자녀 수 - 1)이다 (1명=0, 2명=1, 3명=2 ...). 자격 판별(1명 이상이면 해당)은
    _preference_satisfied가 입력 인원수를 그대로 쓰므로 이 값과 무관하다. MINOR_CHILD_OVER_2YEARS는 입력 인원수 그대로."""
    count = _newborn_child_count(key, request)
    return max(count - 1, 0) if key == "NEWBORN_ADDITIONAL_CHILD" else count


def _newborn_summed_discount(loan, request) -> float:
    """NEWBORN_ADDITIONAL_CHILD/MINOR_CHILD_OVER_2YEARS 두 우대사항의 (1명당 차감율 x 차감 인원수)를 더한 값.
    NEWBORN_ADDITIONAL_CHILD의 차감 인원수는 입력 자녀 수 - 1이다(_discountable_child_count). 대출에 그 우대사항 행이 아예
    없으면(다른 대출들) 0으로, 있어도 차감 인원수가 0이면 0이다."""
    total = 0.0
    for key, count_field in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD.items():
        setting = loan.preferences.get(key)
        if setting is None:
            continue
        total += (setting.discount or 0.0) * _discountable_child_count(key, request)
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


# 2026-10-02: 청년전용 보증부월세대출 "전용" 금리 구조 - 연소득x임차보증금 구간표가 아니라, 보증금 대출은
# 고정금리 하나, 월세대출은 (무이자 기준액을 초과하는 월세 금액이 매달 누적되는) 전혀 다른 계산식을 쓴다.
# RATE_TABLE_BRACKETS_BY_LOAN_TYPE에는 안 들어간다 - _table_base_rate_percent/_has_real_rate에서 따로 분기한다.
YOUTH_MONTHLY_RENT_LOAN_TYPE = "YOUTH_MONTHLY_RENT"


def _table_base_rate_percent(loan, request, listing: dict) -> float | None:
    """기본금리(연 %)를 찾는다. 청년전용 보증부월세대출은 표가 아니라 보증금 대출 금리(고정값, 관리자가
    입력)를 그대로 쓴다. 다른 대출은 대출금리표(loan.rate_table)에서 연소득 구간(행) x 임차보증금 구간(열) -
    둘 다 대출마다 다르다(RATE_TABLE_BRACKETS_BY_LOAN_TYPE)에 해당하는 기본금리를 찾는다. 이자율 결정 방식이
    미정인 대출(그 목록에도, 청년전용 보증부월세대출도 아닌 대출, 2026-10-02)은 표 자체를 읽지 않는다.
    값이 없으면 None(호출하는 쪽이 기준금리 API 값으로 대신한다)."""
    if loan.type == YOUTH_MONTHLY_RENT_LOAN_TYPE:
        return loan.deposit_loan_rate_percent
    brackets = RATE_TABLE_BRACKETS_BY_LOAN_TYPE.get(loan.type)
    if brackets is None or not loan.rate_table:
        return None
    income_idx = _bracket_index(_effective_income(request), brackets["income"])
    deposit_idx = _bracket_index(listing.get("listing_deposit") or 0, brackets["deposit"])
    return loan.rate_table[income_idx][deposit_idx]


def _has_real_rate(loan) -> bool:
    """is_temporary_rate 판별용 - True면 관리자가 입력한 실제 금리(표 또는 청년전용 보증부월세대출의
    고정 보증금 금리)가 있다는 뜻이다. False면 기준금리 API 값을 "입력 대기" 임시값으로 대신 쓴다."""
    if loan.type == YOUTH_MONTHLY_RENT_LOAN_TYPE:
        return loan.deposit_loan_rate_percent is not None
    return loan.type in RATE_TABLE_BRACKETS_BY_LOAN_TYPE and bool(loan.rate_table)


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


def _listing_monthly_rent_ok(loan, listing: dict) -> bool:
    """매물 월세 제한(이하) - 공통 조건이 아니라 이 대출만의 별도 조건(청년전용 보증부월세대출처럼 월세
    매물을 대상으로 하는 대출에서만 쓴다, 2026-10-02). None이면 제한 없음."""
    limit = loan.max_listing_monthly_rent
    return limit is None or (listing.get("listing_monthly_rent") or 0) <= limit


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


def military_extension_years(months: int | None) -> int:
    """군복무기간(개월)이 나이 상한을 몇 년 늘려주는지. 12개월마다 1년이고, 12개월을 1개월이라도 넘기면 1년으로 올림한다
    (1~12개월=1년, 13~24개월=2년, 25~36개월=3년 ...). 미입력/0개월이면 0년."""
    if not months or months <= 0:
        return 0
    return -(-int(months) // 12)  # 올림 나눗셈


def _age_ok(loan, request) -> bool:
    """대출 나이 제한 판별. 최대 나이가 공란이면 군복무기간은 아무 영향이 없고, 최대 나이가 있으면 군복무기간만큼 연장해서 본다.
    최소 나이는 연장하지 않는다 (군복무는 상한을 늘려주는 제도)."""
    max_age = loan.max_age
    if max_age is not None:
        max_age += military_extension_years(getattr(request, "military_service_months", None))
    return policy_matcher._age_ok({"min_age": loan.min_age, "max_age": max_age}, request.age)


def is_eligible(loan, request, listing: dict) -> bool:
    """대출 1종이 매물 1건에 적용되는지. 대상 매물 유형(전세/월세)까지 맞아야 한다."""
    if loan.lease_type != listing.get("lease_type"):
        return False
    return (
        _age_ok(loan, request)
        and _income_ok(loan, request)
        and policy_matcher._asset_ok({"max_asset": loan.max_asset}, request.assets)
        and _listing_deposit_ok(loan, request, listing)
        and _listing_monthly_rent_ok(loan, listing)
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


# 2026-10-03: 카드에서 "왜 이 금리/한도가 나왔는지" 보여주려고 우대사항 라벨을 AI 엔진에도 둔다
# (관리자 화면 라벨 = backend LoanPreferenceKey.java와 같게 유지).
_PREFERENCE_LABELS = {
    "BASIC_LIVELIHOOD": "기초생활수급자", "NEAR_POOR": "차상위계층", "SINGLE_PARENT": "한부모가구",
    "INDEPENDENT_YOUTH": "자립준비청년", "NEWLYWED": "신혼부부(기혼자포함)", "DUAL_INCOME": "맞벌이부부",
    "ONE_CHILD": "1자녀", "TWO_CHILDREN": "2자녀", "MULTI_CHILD": "다자녀가구", "DISABLED": "장애인",
    "MULTICULTURAL": "다문화가구", "ELDERLY_DEPENDENT": "노인부양가구", "ELDERLY_HOUSEHOLD": "고령자가구",
    "NO_HOME": "무주택여부", "SME_EMPLOYED_YOUTH": "중소기업 취업청년", "AGE_UNDER_25": "만 25세 미만",
    "AGE_25_OR_OLDER": "만 25세 이상", "NEWBORN_ADDITIONAL_CHILD": "대출접수일 기준 2년 내 출산한 자녀",
    "MINOR_CHILD_OVER_2YEARS": "대출접수일 기준 출생 후 2년 초과한 미성년 자녀",
}


# 2026-10-03: 홈페이지(주택도시기금) 공통 문구 - "우대금리 적용 후 최종금리가 연 1.0% 미만인 경우에는 연 1.0%로 적용".
# 우대금리를 실제로 깎은 경우에만 하한을 적용한다(기본금리 자체가 1.0% 미만인 대출은 우대가 없으면 그대로 둔다).
MIN_FINAL_RATE_PERCENT = 1.0


def _preference_label(key: str) -> str:
    return _PREFERENCE_LABELS.get(key, key)


def _rate_details(loan, request, listing: dict, market_rate_percent: float) -> dict:
    """최종 금리와 그 근거. _final_rate_percent의 계산을 그대로 하되, 카드에 보여줄 내역(기본금리, 해당하는
    우대사항별 차감값과 실제 적용 여부, 상한)을 같이 돌려준다. 계산식은 _final_rate_percent 독스트링 참고."""
    base_rate = _table_base_rate_percent(loan, request, listing)
    if base_rate is None:
        base_rate = market_rate_percent
    normal = [
        (key, setting.discount or 0.0)
        for key, setting in loan.preferences.items()
        if key not in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD and _preference_satisfied(key, request)
    ]
    newborn_sum = _newborn_summed_discount(loan, request)
    candidates = [d for _, d in normal] + [newborn_sum]
    raw_discount = max(candidates, default=0.0)
    # 동률이면 max()가 앞쪽(일반 우대사항)을 고르므로 신생아 합산은 "엄격하게 더 클 때"만 이긴다.
    newborn_wins = newborn_sum > 0 and newborn_sum > max((d for _, d in normal), default=0.0)
    first_normal_winner = None
    if not newborn_wins:
        first_normal_winner = next((k for k, d in normal if d == raw_discount and d > 0), None)
    items = [
        {"key": k, "label": _preference_label(k), "discount_percent": d, "count": None,
         "applied": k == first_normal_winner}
        for k, d in normal if d > 0
    ]
    for k in _NEWBORN_SUMMED_PREFERENCE_COUNT_FIELD:
        setting = loan.preferences.get(k)
        count = _newborn_child_count(k, request)  # 화면에는 사용자가 입력한 자녀 수를 그대로 보여준다
        effective = _discountable_child_count(k, request)  # 차감에 곱해지는 수 (NEWBORN_ADDITIONAL_CHILD는 입력 - 1)
        if setting is None or effective <= 0 or not (setting.discount or 0.0) > 0:
            continue
        items.append({
            "key": k, "label": _preference_label(k), "discount_percent": round(setting.discount * effective, 2),
            "count": count, "applied": newborn_wins,
        })
    cap = _discount_cap_percent(loan, request)
    discount = raw_discount if cap is None else min(raw_discount, cap)
    final_rate = max(0.0, base_rate - discount)
    # 최종 금리 하한(연 1.0%): 우대로 깎아서 1.0% 밑으로 내려갈 때만 1.0%로 올린다
    floor_applied = discount > 0 and final_rate < MIN_FINAL_RATE_PERCENT
    if floor_applied:
        final_rate = MIN_FINAL_RATE_PERCENT
    return {
        "base_rate_percent": round(base_rate, 2),
        "discount_items": items,
        "discount_percent": round(discount, 2),
        "discount_cap_percent": cap,
        "discount_capped": cap is not None and raw_discount > cap,
        "rate_floor_applied": floor_applied,
        "rate_percent": round(final_rate, 2),
    }


def _final_rate_percent(loan, request, listing: dict, market_rate_percent: float) -> float:
    """기본금리(대출금리표가 있으면 그 표의 값, 없으면 market_rate_percent - 기준금리 API 값)에서, 사용자가
    해당하는 우대사항 중 "가장 큰 우대금리 차감 하나만" 뺀 최종 금리(연 %) - 여러 우대사항에 해당해도 중복으로
    합산하지 않는다(2026-10-01, 관리자 요청으로 sum()에서 max()로 변경. 모든 대출 공통). required 여부와
    무관하게 "해당하면" 차감 후보가 된다(필수 자격 판별과는 별개 계산식). 단, NEWBORN_ADDITIONAL_CHILD/
    MINOR_CHILD_OVER_2YEARS 둘은 예외로 서로 sum()해서 "우대사항 하나"의 후보값으로 만든 뒤에 이 max() 풀에
    섞는다(_newborn_summed_discount, 2026-10-02) - 입력은 따로 받지만 최종 반영은 합쳐서 하나로 경쟁시킨다.
    그 값이 대출별 상한(cap)을 넘으면 상한으로 깎인다(_discount_cap_percent 참고, 2026-10-02). 0% 밑으로는
    내려가지 않는다. 내역이 필요하면 _rate_details를 쓴다."""
    return _rate_details(loan, request, listing, market_rate_percent)["rate_percent"]


# 2026-10-03: 우대 한도 재반영 내역 - (응답 키, 화면 라벨, 단위, 공통값 getter, 재반영 getter)
_LIMIT_REFLECTION_FIELDS = (
    ("max_listing_deposit", "매물 보증금 제한 (이하)", "만원", lambda l: l.max_listing_deposit, lambda s: s.override_max_listing_deposit),
    ("max_loan_amount", "최대 대출금액", "만원", lambda l: l.max_loan_amount, lambda s: s.override_max_loan_amount),
    ("max_loan_ratio_percent", "최대 대출금 비율한도", "%", lambda l: l.max_loan_ratio_percent, lambda s: s.override_max_loan_ratio_percent),
    ("max_exclusive_area", "전용면적 (이하)", "㎡", lambda l: l.max_exclusive_area, lambda s: s.override_max_exclusive_area),
    ("max_income_single", "연소득 (이하) - 개인", "만원", lambda l: l.max_income_single, lambda s: s.override_max_income_single),
    ("max_income_couple", "연소득 (이하) - 부부합산", "만원", lambda l: l.max_income_couple, lambda s: s.override_max_income_couple),
)


def _limit_reflections(loan, request) -> list[dict]:
    """사용자가 해당하는 우대사항이 공통 한도를 다른 값으로 재반영한 항목들(_effective_limit과 같은 규칙:
    해당하는 재반영 값 중 가장 큰 값이 공통값을 대체한다). 값이 실제로 바뀐 항목만 돌려준다."""
    out = []
    for field, label, unit, base_getter, override_getter in _LIMIT_REFLECTION_FIELDS:
        base = base_getter(loan)
        effective = _effective_limit(loan, request, base, override_getter)
        if base is None or effective == base:
            continue
        sources = [
            _preference_label(key)
            for key, setting in loan.preferences.items()
            if override_getter(setting) == effective and _preference_satisfied(key, request)
        ]
        out.append({"field": field, "label": label, "unit": unit, "base": base, "effective": effective, "sources": sources})
    return out


# 2026-10-02: 월세대출 총 이자를 보여줄 기준 기간. 사용자가 실제로 얼마나 거주할지 알 방법이 없어서(전세
# 계약 기간처럼 입력받는 값이 없음), 표준 임대 계약 기간인 "2년"을 고정값으로 가정한다 - 관리자가 대출마다
# 바꿀 값이 아니라 코드에 고정해둔다(admin/loans.html에도 입력칸을 안 둔다).
_RENT_LOAN_ASSUMED_TERM_MONTHS = 24
# 2026-10-03: 월세대출 "월 한도" 입력칸을 없앴다(계산과 무관한 예시값이었음) - 대신 "대출 기간 중 최대 월세대출액"
# 1200만원을 안내문 전용 고정값으로 카드에 보여준다(자격 판별/실질주거비 계산에는 안 쓴다).
_RENT_LOAN_TOTAL_CAP_MANWON = 1200


def _rent_loan_amount_manwon(loan, listing: dict) -> float:
    """월세대출로 이번 달 충당되는 월세 금액(만원 - 무이자/유이자 구간 전부 포함한 전체 월세).
    보증부월세대출은 이 금액을 집주인에게 대신 지급하는 구조라, 세입자는 매달 이 금액만큼 월세를 현금으로
    안 내는 대신 대출 잔액이 쌓인다(실질주거비 "월세 대출 시" 계산에 쓴다 - _loan_result 참고). 월 한도는
    계산에 쓰지 않는다(2026-10-03, 입력칸 제거) - 설정이 미완성이면(기준액/금리 중 하나라도 비어 있으면) 0이다."""
    if loan.type != YOUTH_MONTHLY_RENT_LOAN_TYPE:
        return 0.0
    if loan.monthly_rent_loan_free_threshold_manwon is None or loan.monthly_rent_loan_rate_percent is None:
        return 0.0
    return float(listing.get("listing_monthly_rent") or 0)


def _rent_loan_configured(loan) -> bool:
    """월세대출 구조(무이자 기준액 + 초과분 금리)가 설정된 청년전용 보증부월세대출인지."""
    return (
        loan.type == YOUTH_MONTHLY_RENT_LOAN_TYPE
        and loan.monthly_rent_loan_free_threshold_manwon is not None
        and loan.monthly_rent_loan_rate_percent is not None
    )


def _rent_loan_total_interest_won(loan, listing: dict) -> float:
    """청년전용 보증부월세대출 "전용" - 월세대출(무이자 기준액 초과분)의 2년(_RENT_LOAN_ASSUMED_TERM_MONTHS)
    총 이자(원). 월세대출은 매달 (월세 중 무이자 기준액을 초과하는 금액)만큼 추가로 빌리는 구조라, k번째 달의
    누적 대출 잔액은 "초과금액 x k"이고 그 달 이자는 "잔액 x 연금리/12"다 - 이걸 2년 동안 합산한 닫힌 형태가
    "초과금액(원) x 연금리 x 24 x 25/2/12"다(등차수열 합). 월 단위 실질주거비(effective_cost)와는 별개의
    "총 이자" 수치라 월 단위로 나누지 않고 그대로 보여준다."""
    loan_amount_manwon = _rent_loan_amount_manwon(loan, listing)
    if loan_amount_manwon <= 0:
        return 0.0
    threshold = loan.monthly_rent_loan_free_threshold_manwon
    rate = loan.monthly_rent_loan_rate_percent
    excess_manwon = max(0.0, loan_amount_manwon - threshold)
    if excess_manwon <= 0:
        return 0.0
    excess_won = excess_manwon * 10000
    term = _RENT_LOAN_ASSUMED_TERM_MONTHS
    return round(excess_won * (rate / 100) * (term * (term + 1) / 2) / 12)


def _rent_loan_monthly_interest_won(loan, listing: dict) -> float:
    """_rent_loan_total_interest_won(2년 총 이자)을 _RENT_LOAN_ASSUMED_TERM_MONTHS로 나눈 "24개월 환산"
    평균 월 이자(원) - 실제로는 회차마다 다르지만(1회차 250원 ~ 24회차 6,250원씩 늘어남), 카드에는 보기 쉽게
    평균값 하나로 보여준다(2026-10-02, 총액과 같이 보여줘서 "월 환산"이라는 걸 명확히 한다)."""
    total = _rent_loan_total_interest_won(loan, listing)
    return round(total / _RENT_LOAN_ASSUMED_TERM_MONTHS) if total > 0 else 0.0


def _loan_result(loan, request, listing: dict, market_rate_percent: float) -> dict:
    """이 대출을 신청했을 때의 "[대출이름] 실질주거비". 보증금전환 실질거주비와 같은 방식(월세+관리비+이자)이되,
    이자는 매물 보증금 전체가 아니라 "매물 보증금 - 사용자가 지금 가진 보증금(request.deposit)"만큼, 즉 대출로
    메워야 하는 부족분에 대해서만 계산한다(자기 돈으로 낼 수 있는 만큼은 대출이 필요 없다).
    보유 보증금이 매물 보증금과 같거나 더 많으면(대출이 필요 없으면) 부족분이 0이라 이자도 0이 된다.
    청년전용 보증부월세대출은 보증금이 충분해 대출이 필요 없어도(loan_principal=0) 월세대출은 별개로 받을
    수 있다 - effective_cost("월세 미대출 시")는 기존 그대로 두고, "월세 대출 시" 버전을 따로 추가로 담아
    보낸다. 월세대출로 충당되는 금액(rent_loan_amount_manwon)만큼은 매달 현금으로 안 내는 대신 대출 잔액이
    쌓이는 구조라, "월세 대출 시" 실질주거비는 그 금액을 월세에서 뺀 값 + 관리비 + 보증금대출이자(위와 동일,
    보유 보증금 차감 그대로 유지) + 월세대출 이자(24개월 환산)로 계산한다. 월세대출 전체 한도(고정 1200만원,
    rent_loan_total_cap_manwon)는 자격 판별에는 안 쓰고 카드 안내문에 참고 정보로만 보낸다(2026-10-02)."""
    rate_details = _rate_details(loan, request, listing, market_rate_percent)
    rate = rate_details["rate_percent"]
    listing_deposit = listing.get("listing_deposit") or 0
    loan_principal = max(0, listing_deposit - (request.deposit or 0))
    monthly_interest = round(loan_principal * rate / 100 / 12, 1)
    rent = listing.get("listing_monthly_rent") or 0
    maintenance = listing.get("maintenance_fee") or 0
    configured = _rent_loan_configured(loan)
    rent_loan_amount_manwon = _rent_loan_amount_manwon(loan, listing)
    rent_loan_monthly_interest_won = _rent_loan_monthly_interest_won(loan, listing)
    rent_after_rent_loan = max(0.0, rent - rent_loan_amount_manwon)
    return {
        "type": loan.type,
        "name": loan.name,
        "rate_percent": rate,
        # 2026-10-03: 금리/한도가 왜 이렇게 나왔는지 카드에서 보여주는 근거 (기본금리, 우대사항별 차감, 한도 재반영)
        "base_rate_percent": rate_details["base_rate_percent"],
        "discount_percent": rate_details["discount_percent"],
        "discount_cap_percent": rate_details["discount_cap_percent"],
        "discount_capped": rate_details["discount_capped"],
        "rate_floor_applied": rate_details["rate_floor_applied"],
        "discount_items": rate_details["discount_items"],
        "limit_reflections": _limit_reflections(loan, request),
        # 기본금리가 어디서 왔는지: table=소득x보증금 금리표, fixed=관리자가 입력한 고정 보증금 대출 금리
        # (청년전용 보증부월세대출), market=기준금리 API 임시값 (_has_real_rate와 같은 기준)
        "base_rate_kind": (
            "market" if not _has_real_rate(loan)
            else "fixed" if loan.type == YOUTH_MONTHLY_RENT_LOAN_TYPE else "table"
        ),
        # 청년전용 보증부월세대출 월세대출 구조 (카드의 "월세 대출 시" 근거 표시용) - 다른 대출은 None
        "rent_loan_free_threshold_manwon": loan.monthly_rent_loan_free_threshold_manwon if configured else None,
        "rent_loan_rate_percent": loan.monthly_rent_loan_rate_percent if configured else None,
        # True면 이 대출에 실제 금리표가 없어(또는 아예 적용 대상이 아니라서) 기준금리 API 값을 "입력 대기"
        # 임시값으로 대신 쓴 것 - _has_real_rate 참고.
        "is_temporary_rate": not _has_real_rate(loan),
        "loan_principal": loan_principal,  # 대출로 메우는 부족분 (매물 보증금 - 보유 보증금, 0 이상)
        "monthly_interest": monthly_interest,
        "effective_cost": round(rent + maintenance + monthly_interest, 1),  # "월세 미대출 시" (기존 그대로)
        # 청년전용 보증부월세대출 전용 - 다른 대출은 항상 0/None이다.
        "rent_loan_total_interest": _rent_loan_total_interest_won(loan, listing),
        "rent_loan_monthly_interest": rent_loan_monthly_interest_won,
        "rent_loan_total_cap_manwon": _RENT_LOAN_TOTAL_CAP_MANWON if configured else None,
        "rent_loan_amount_manwon": rent_loan_amount_manwon,  # 이번 달 월세대출로 충당되는 금액(만원)
        # "월세 대출 시" 실질주거비 = (월세 - 월세대출 충당액) + 관리비 + 보증금대출이자 + 월세대출이자(24개월 환산)
        "rent_loan_effective_cost": round(
            rent_after_rent_loan + maintenance + monthly_interest + rent_loan_monthly_interest_won / 10000, 1
        ) if rent_loan_amount_manwon > 0 else 0.0,
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
