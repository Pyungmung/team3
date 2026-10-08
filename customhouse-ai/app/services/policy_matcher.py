"""
[담당: 송귀성] 청년 주거지원 정책 자동 매칭
관리자 수정 > 주거지원정책에서 관리하는 서울시/자치구 청년 주거지원 정책(DB)과 사용자 조건·매물 지역을 비교해서
적용 가능한 정책을 찾아준다. 정책 목록은 백엔드가 DB에서 읽어 진단 요청(request.housing_policies)에 실어 보낸다.

2026-10-08: 예전에는 docs/housing_policy_list.csv를 scripts/convert_policies.py로 policies.json으로 바꿔 읽었다.
정책이 매달 바뀌고 관심정책 사용자에게 변경/삭제 알림을 보내야 해서 DB로 옮겼고, 이 파일에서는 그 JSON/CSV를 더 읽지 않는다.
조건 판별 규칙(나이/소득/자산/중위소득%/기초수급/중소기업/신혼부부/무주택, 미입력 시 관대 처리)은 옮기기 전과 똑같다.

2026-09-17: 기존에는 정책이 "월 20만원 지원" 같은 정형 수치(monthly_benefit,
loan_rate_annual_percent)를 가지고 있어서 real_housing_cost 계산에도 반영됐지만,
정책 지원혜택이 자유 텍스트(대출 조건/현물 지원/서비스 등 제각각)라 정형화된
수치를 뽑아낼 수 없다. 그래서 정책 매칭은 "주거정책 추천" 표에 보여주기 위한
정보성 매칭으로만 쓰이고(agency/name/description), 실질 주거비(real_housing_cost)
계산에는 반영하지 않는다 (government_support는 항상 0 - calculator.py 참고).

2026-09-30: 기준중위소득 100%(1인가구, 월) 값은 관리자 수정 > 기준소득관리에서 저장한 값
(request.income_standard.median_income_100_percent_monthly_won, 원 단위)을 쓴다. 이 값이
없으면(조회 실패 등) DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON으로 폴백한다 - _median_income_100_percent_monthly_manwon 참고.
"""

# 기준소득관리 값을 못 읽었을 때만 쓰는 안전장치(만원 단위). 예전 policies.json(CSV 15열)에 들어 있던 값 그대로다.
DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON = 256.4238


def _median_income_100_percent_monthly_manwon(request) -> float:
    """기준중위소득 100%(1인가구, 월, 만원). 관리자 수정 > 기준소득관리에 저장된 값(원 단위)이 있으면 ÷10000해서
    쓰고, 없으면 DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON(예전 CSV/JSON에 있던 값)으로 폴백한다."""
    income_standard = getattr(request, "income_standard", None)
    won = income_standard.median_income_100_percent_monthly_won if income_standard else None
    if won is not None:
        return won / 10000
    return DEFAULT_MEDIAN_INCOME_100_MONTHLY_MANWON


def _region_ok(policy: dict, building_region: str) -> bool:
    """정책 지역이 "서울"이면 서울 전역(모든 매물 지역)에 적용되는 정책이라 통과.
    "중랑구"처럼 특정 자치구면 그 매물의 지역(region)과 정확히 일치해야 통과."""
    policy_region = policy.get("region")
    if not policy_region or policy_region == "서울":
        return True
    return policy_region == building_region


def _age_ok(policy: dict, age: int | None) -> bool:
    """나이 미입력 시 일단 후보로 포함 (보수적으로 탈락시키지 않음)."""
    if age is None:
        return True
    min_age = policy.get("min_age")
    max_age = policy.get("max_age")
    if min_age is not None and age < min_age:
        return False
    if max_age is not None and age > max_age:
        return False
    return True


def _annual_income_ok(policy: dict, request) -> bool:
    """개인/부부합산 연소득(이하) 조건 - 본인 연소득과 부부합산 연소득 중 더 큰 값으로 심사."""
    max_annual_income = policy.get("max_annual_income")
    if max_annual_income is None:
        return True
    annual_income = max(request.annual_income, request.couple_annual_income or 0)
    return annual_income <= max_annual_income


def _median_income_ok(policy: dict, request) -> bool:
    """기준중위소득(%) 조건 - 1인가구 기준중위소득 100%(월/만원)에
    정책별 퍼센트를 곱해 실제 월 소득 상한(만원)을 구하고, 실효 월소득과 비교한다."""
    percent = policy.get("median_income_percent")
    if percent is None:
        return True
    threshold_monthly_manwon = _median_income_100_percent_monthly_manwon(request) * (percent / 100)
    return request.effective_monthly_income <= threshold_monthly_manwon


def _asset_ok(policy: dict, assets: int | None) -> bool:
    """자산 미입력 시 나이와 동일하게 일단 후보로 포함(보수적으로 탈락시키지 않음)."""
    max_asset = policy.get("max_asset")
    if max_asset is None or assets is None:
        return True
    return assets <= max_asset


def _no_household_ok(policy: dict, no_householder: bool | None) -> bool:
    """무주택조건 - 미입력(None) 시 나이/자산처럼 일단 포함, 명시적으로 "무주택 아님"이라고
    답한 경우에만 탈락시킨다 (기존 버팀목대출 조건 판별과 동일한 관대한 처리)."""
    if not policy.get("require_no_household"):
        return True
    return no_householder is not False


def _preferential_flags_ok(policy: dict, preferential_statuses: list[str], job_type: str | None) -> bool:
    """기초수급자/중소기업재직/신혼부부 조건 - 전부 자기신고 항목이라, 정책이 요구하는데
    미입력(체크 안 함)이면 보수적으로 탈락시킨다 (나이/자산과 달리 "모르면 포함"하지 않음)."""
    if policy.get("require_basic_livelihood") and "BASIC_LIVELIHOOD" not in preferential_statuses:
        return False
    if policy.get("require_sme") and job_type != "SME":
        return False
    if policy.get("require_newlywed") and "NEWLYWED" not in preferential_statuses:
        return False
    return True


def match_display_policies(request, building_region: str) -> list[dict]:
    """매물 지역과 사용자 조건에 맞는 정책을 찾아 "주거정책 추천" 표에 보여줄 형태로 반환한다.
    정책 목록은 request.housing_policies(백엔드가 DB에서 읽어 실어 보낸 것)이고, 그 순서(= 관리자 화면 입력 순서)대로 돌려준다.
    (실질 주거비 계산에는 반영하지 않는 정보성 매칭 - 모듈 docstring 참고)"""
    matched = []
    for item in request.housing_policies:
        policy = item.model_dump()
        # "정책 대출 활용"을 해제한 사용자에게는 대출 상품을 추천하지 않는다.
        if policy.get("is_loan") and not request.use_loan_policy:
            continue
        if not _region_ok(policy, building_region):
            continue
        if not _age_ok(policy, request.age):
            continue
        if not _annual_income_ok(policy, request):
            continue
        if not _median_income_ok(policy, request):
            continue
        if not _asset_ok(policy, request.assets):
            continue
        if not _no_household_ok(policy, request.no_householder):
            continue
        if not _preferential_flags_ok(policy, request.preferential_statuses, request.job_type):
            continue

        matched.append(
            {
                "id": policy["id"],  # 정책 DB id - 리포트의 관심정책 하트가 이 값으로 정책을 가리킨다
                "region": policy.get("region") or "서울",  # "서울"이면 서울 전역 공통 정책, 자치구 이름이면 그 자치구 정책
                "agency": policy["agency"],
                "name": policy["name"],
                "description": policy["description"],
                "link": policy.get("link") or "",  # 정책 안내/신청 홈페이지 (없으면 "" - 화면의 [이동] 버튼이 비활성)
            }
        )

    return matched
