"""
[담당: 송귀성] 전세자금대출 자격 판별 + 실질주거비 계산 (관리자 수정 > 전세자금대출에서 저장한 조건 <-> 사용자 조건·매물)
관리자 화면에서 입력한 대출 조건(나이/연소득/총자산/매물 보증금/전용면적/필수 우대사항/대출 한도)으로, 추천된 매물마다
"이 대출을 신청할 수 있는지"를 판별하고, 신청 가능하면 "[대출이름] 실질주거비"를 계산해 리포트 카드에 보여준다.

2026-09-29: 최대 대출금 비율한도(%, 매물 보증금 기준)와 최대 대출금액(절대 상한)을 추가했다. 대출로 메워야 하는
부족분(매물 보증금 - 보유 보증금)이 이 한도(둘 다 있으면 더 낮은 쪽)를 넘으면, 대출로도 그 매물을 감당할 수
없으므로 "신청 가능"에서 뺀다 - _loan_amount_ok/_max_loan_available 참고.

판별 방식은 주거정책 추천(policy_matcher.py)과 같은 규칙이다 - 나이/자산/무주택은 미입력이면 일단 포함(관대),
자기신고 항목(기초수급 등)과 직업 유형은 필수로 요구하는데 체크하지 않았으면 탈락(보수적). 가능한 조건 함수는
policy_matcher의 것을 그대로 재사용한다.

필수(required) 우대사항과 우대금리 차감(discount)은 서로 다른 조건식이다:
  - required: 대출 "신청 가능 여부"(자격)를 가른다 - 체크된 우대사항이 필수인데 사용자가 해당하지 않으면 그 대출 자체가 안 보인다.
  - discount: 자격과 무관하게, 사용자가 그 우대사항에 해당하면(필수든 아니든) 최종 금리에서 깎아주는 값이다.
2026-09-29: 실제 대출별 금리표(구간별 금리 등)는 아직 없어서, 관리자가 표 이미지를 보내 분석하기 전까지는
기본금리를 5개 대출 공통 3%(DEFAULT_BASE_RATE_PERCENT)로 임시 고정하고, 여기서 우대금리 차감만 반영한다.
대출 조건은 백엔드가 DB에서 읽어 요청(request.loan_products)에 실어 보낸다 - 이 모듈은 DB나 파일을 읽지 않는다.
"""
from app.services import policy_matcher

# 표 이미지를 분석해 실제 대출별(+구간별) 금리를 넣기 전까지 쓰는 임시 공통 기본금리 (연 %). 2026-09-29 확정 전 임시값.
DEFAULT_BASE_RATE_PERCENT = 3.0

# 관리자 화면의 우대사항 코드 -> 요청의 preferential_statuses 코드. 이름이 같으면 생략한다.
# (관리자 화면은 NEAR_POOR, 주거조건 입력은 NEAR_POVERTY로 코드가 달라서 여기서 맞춘다)
_STATUS_CODE_BY_PREFERENCE = {"NEAR_POOR": "NEAR_POVERTY"}
_SELF_REPORTED_PREFERENCES = ("BASIC_LIVELIHOOD", "NEAR_POOR", "SINGLE_PARENT", "INDEPENDENT_YOUTH", "NEWLYWED", "MULTI_CHILD")


def _preference_satisfied(key: str, request) -> bool:
    """사용자가 이 우대사항 조건에 해당하는지 (required 여부와 무관 - 필수 판별과 우대금리 차감이 공용으로 쓴다).
    - 자기신고 항목(기초수급/차상위/한부모/자립준비청년/신혼부부/다자녀): 체크했어야 해당
    - 중소기업 취업청년: 주거조건의 직업 유형이 SME(중소기업)여야 해당
    - 무주택: 명시적으로 "무주택 아님"이 아니면 해당 (policy_matcher._no_household_ok와 같은 관대한 처리)"""
    if key in _SELF_REPORTED_PREFERENCES:
        return _STATUS_CODE_BY_PREFERENCE.get(key, key) in request.preferential_statuses
    if key == "SME_EMPLOYED_YOUTH":
        return request.job_type == "SME"
    if key == "NO_HOME":
        return policy_matcher._no_household_ok({"require_no_household": True}, request.no_householder)
    return False  # 관리자 화면에 없는 낯선 코드는 해당 없음으로 본다


def _income_ok(loan, request) -> bool:
    """연소득(이하): 판정 소득 = max(본인, 부부합산) (policy_matcher._annual_income_ok와 같다).
    부부합산 소득을 입력했고 부부합산 상한이 있으면 그 상한으로, 아니면 개인 상한으로 비교한다."""
    income = max(request.annual_income, request.couple_annual_income or 0)
    limit = loan.max_income_couple if (request.couple_annual_income and loan.max_income_couple is not None) else loan.max_income_single
    return limit is None or income <= limit


def _listing_deposit_ok(loan, listing: dict) -> bool:
    return loan.max_listing_deposit is None or (listing.get("listing_deposit") or 0) <= loan.max_listing_deposit


def _area_ok(loan, listing: dict) -> bool:
    """전용면적(이하): 면적 정보가 없는 매물(0/None)은 일단 포함."""
    area = listing.get("exclusive_area")
    return loan.max_exclusive_area is None or not area or area <= loan.max_exclusive_area


def _required_preferences_ok(loan, request) -> bool:
    """필수(required)로 표시된 우대사항을 모두 충족해야 한다 (하나라도 required인데 해당 안 하면 탈락)."""
    return all(_preference_satisfied(key, request) for key, setting in loan.preferences.items() if setting.required)


def _max_loan_available(loan, listing: dict) -> float | None:
    """이 대출로 이 매물에 실제 빌릴 수 있는 최대 금액(만원) = 매물 보증금의 비율한도, 절대 상한 중 더 낮은 쪽
    (관리자가 최대 대출금 비율한도(%)와 최대 대출금액을 둘 다 넣으면 더 작은 쪽이 실제 한도다). 둘 다 비어 있으면 제한 없음(None)."""
    limits = []
    if loan.max_loan_ratio_percent is not None:
        limits.append((listing.get("listing_deposit") or 0) * loan.max_loan_ratio_percent / 100)
    if loan.max_loan_amount is not None:
        limits.append(loan.max_loan_amount)
    return min(limits) if limits else None


def _loan_amount_ok(loan, request, listing: dict) -> bool:
    """대출로 메워야 하는 부족분(매물 보증금 - 보유 보증금)이 이 대출의 최대 대출 가능액을 넘으면 이 대출로는
    이 매물을 감당할 수 없으므로 탈락한다. 부족분이 0이면(이미 대출 없이 충분하면) 한도와 무관하게 통과한다."""
    shortfall = max(0, (listing.get("listing_deposit") or 0) - (request.deposit or 0))
    if shortfall == 0:
        return True
    available = _max_loan_available(loan, listing)
    return available is None or shortfall <= available


def is_eligible(loan, request, listing: dict) -> bool:
    """대출 1종이 매물 1건에 적용되는지. 대상 매물 유형(전세/월세)까지 맞아야 한다."""
    if loan.lease_type != listing.get("lease_type"):
        return False
    return (
        policy_matcher._age_ok({"min_age": loan.min_age, "max_age": loan.max_age}, request.age)
        and _income_ok(loan, request)
        and policy_matcher._asset_ok({"max_asset": loan.max_asset}, request.assets)
        and _listing_deposit_ok(loan, listing)
        and _area_ok(loan, listing)
        and _required_preferences_ok(loan, request)
        and _loan_amount_ok(loan, request, listing)
    )


def _final_rate_percent(loan, request) -> float:
    """기본금리(DEFAULT_BASE_RATE_PERCENT)에서, 사용자가 해당하는 우대사항의 우대금리를 모두 뺀 최종 금리(연 %).
    required 여부와 무관하게 "해당하면" 차감한다 (필수 자격 판별과는 별개 계산식). 0% 밑으로는 내려가지 않는다."""
    discount = sum(
        setting.discount or 0.0
        for key, setting in loan.preferences.items()
        if _preference_satisfied(key, request)
    )
    return round(max(0.0, DEFAULT_BASE_RATE_PERCENT - discount), 2)


def _loan_result(loan, request, listing: dict) -> dict:
    """이 대출을 신청했을 때의 "[대출이름] 실질주거비". 보증금전환 실질거주비와 같은 방식(월세+관리비+이자)이되,
    이자는 매물 보증금 전체가 아니라 "매물 보증금 - 사용자가 지금 가진 보증금(request.deposit)"만큼, 즉 대출로
    메워야 하는 부족분에 대해서만 계산한다(자기 돈으로 낼 수 있는 만큼은 대출이 필요 없다).
    보유 보증금이 매물 보증금과 같거나 더 많으면(대출이 필요 없으면) 부족분이 0이라 이자도 0이 된다."""
    rate = _final_rate_percent(loan, request)
    listing_deposit = listing.get("listing_deposit") or 0
    loan_principal = max(0, listing_deposit - (request.deposit or 0))
    monthly_interest = round(loan_principal * rate / 100 / 12, 1)
    rent = listing.get("listing_monthly_rent") or 0
    maintenance = listing.get("maintenance_fee") or 0
    return {
        "type": loan.type,
        "name": loan.name,
        "rate_percent": rate,
        "loan_principal": loan_principal,  # 대출로 메우는 부족분 (매물 보증금 - 보유 보증금, 0 이상)
        "monthly_interest": monthly_interest,
        "effective_cost": round(rent + maintenance + monthly_interest, 1),
    }


def match_eligible_loans(request, listing: dict) -> list[dict]:
    """매물 1건에 신청 가능한 대출 목록(각각 실질주거비 포함). 저장된 대출이 없거나 "정책 대출 활용"을 해제했으면
    빈 목록이다 (policy_matcher가 대출 상품을 추천하지 않는 것과 같다)."""
    if not request.loan_products or not request.use_loan_policy:
        return []
    return [_loan_result(loan, request, listing) for loan in request.loan_products if is_eligible(loan, request, listing)]
