"""
[담당: 송귀성] 청년 주거지원 정책 자동 매칭
사용자의 소득/나이/무주택 여부/직업종류/우대사항과 policies.json의 자격 조건을 비교하여
적용 가능한 정책(월세지원, 저금리 대출 등)을 찾아준다.

주의: conditions 값은 예시(샘플) 기준이며, 실제 서비스에서는 복지로/마이홈 등
공공 정책 API 데이터로 교체해야 한다.

2026-09-16: 마이페이지 프로필 확장과 함께 job_type(직업종류)/preferential_statuses(우대사항)
판별을 추가했다. 나이/자산과 달리 우대사항은 "본인이 해당한다고 명시적으로 체크한" 자기신고
항목이라, 미입력(빈 리스트)이면 그 우대사항을 요구하는 정책은 보수적으로 탈락시킨다
(나이/자산처럼 "모르면 일단 포함"하지 않음 - 없는 자격을 있다고 가정하면 안 되니까).
"""
import json
from functools import lru_cache

from app.core.config import settings


@lru_cache(maxsize=1)
def load_policies() -> list[dict]:
    """지역 평균이 아니라 개별 매물 단위로 추천하면서 건물 후보마다 이 함수가 호출되므로,
    (변하지 않는) 정책 JSON을 매번 디스크에서 다시 읽지 않도록 캐싱한다."""
    with open(settings.policies_file, encoding="utf-8") as f:
        return json.load(f)["policies"]


def _age_ok(conditions: dict, age: int | None) -> bool:
    if age is None:
        return True  # 나이 미입력 시 일단 후보로 포함 (보수적으로 가정하지 않음)
    return conditions.get("min_age", 0) <= age <= conditions.get("max_age", 200)


def _asset_ok(conditions: dict, assets: int | None) -> bool:
    """실제 청년 정책(청년월세지원, 버팀목대출 등)은 소득뿐 아니라 순자산 기준도 함께 본다.
    자산 미입력 시 나이와 동일하게 일단 후보로 포함(보수적으로 탈락시키지 않음)."""
    if assets is None:
        return True
    return assets <= conditions.get("max_asset", 10 ** 9)


def _job_type_ok(conditions: dict, job_type: str | None) -> bool:
    """정책에 eligible_job_types가 지정돼 있으면 그중 하나여야 통과한다.
    지정 안 돼있으면(대부분의 정책) 직업종류를 안 본다는 뜻이라 무조건 통과."""
    eligible = conditions.get("eligible_job_types")
    if not eligible:
        return True
    return job_type in eligible


def _preferential_ok(conditions: dict, preferential_statuses: list[str]) -> bool:
    """정책에 required_preferential_status가 지정돼 있으면 사용자가 그중 하나라도
    가지고 있어야 통과한다. 자기신고 항목이라 미입력(빈 리스트)이면 탈락시킨다."""
    required = conditions.get("required_preferential_status")
    if not required:
        return True
    return bool(set(required) & set(preferential_statuses))


def find_eligible_loan_policy(request) -> dict | None:
    """자격 조건을 만족하는 저금리 정책 대출을 찾는다. (버팀목대출 등)"""
    for policy in load_policies():
        if policy["type"] != "LOAN":
            continue
        conditions = policy["conditions"]

        if not _age_ok(conditions, request.age):
            continue
        if not _asset_ok(conditions, request.assets):
            continue
        if not _job_type_ok(conditions, request.job_type):
            continue
        if not _preferential_ok(conditions, request.preferential_statuses):
            continue
        if request.effective_monthly_income > conditions.get("max_monthly_income", 10_000):
            continue
        if conditions.get("require_no_household") and request.no_householder is False:
            continue

        return policy
    return None


def match_policies(request, rent: int, policy_loan: dict | None) -> list[dict]:
    """정책 목록 중 실제로 적용 가능한 것들을 매칭 결과(dict) 리스트로 반환한다."""
    matched = []

    for policy in load_policies():
        conditions = policy["conditions"]

        if policy["type"] == "SUBSIDY":
            if not _age_ok(conditions, request.age):
                continue
            if not _asset_ok(conditions, request.assets):
                continue
            if not _job_type_ok(conditions, request.job_type):
                continue
            if not _preferential_ok(conditions, request.preferential_statuses):
                continue
            if request.effective_monthly_income > conditions.get("max_monthly_income", 10_000):
                continue
            if rent > conditions.get("max_rent", 10_000):
                continue
            if rent <= 0:
                continue  # 월세 부담이 없다면 월세지원 실익 없음

            matched.append(
                {
                    "id": policy["id"],
                    "name": policy["name"],
                    "type": policy["type"],
                    "description": policy["description"],
                    "monthly_benefit": policy["monthly_benefit"],
                    "loan_rate_annual_percent": None,
                }
            )

        elif policy["type"] == "LOAN" and policy_loan and policy["id"] == policy_loan["id"]:
            matched.append(
                {
                    "id": policy["id"],
                    "name": policy["name"],
                    "type": policy["type"],
                    "description": policy["description"],
                    "monthly_benefit": 0,  # 대출은 금리 우대로 이미 loan_interest 계산에 반영됨
                    "loan_rate_annual_percent": policy["loan_rate_annual_percent"],
                }
            )

    return matched
