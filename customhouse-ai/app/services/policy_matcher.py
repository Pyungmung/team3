"""
[담당: 송귀성] 청년 주거지원 정책 자동 매칭
사용자의 소득/나이/무주택 여부와 policies.json의 자격 조건을 비교하여
적용 가능한 정책(월세지원, 저금리 대출 등)을 찾아준다.

주의: conditions 값은 예시(샘플) 기준이며, 실제 서비스에서는 복지로/마이홈 등
공공 정책 API 데이터로 교체해야 한다.
"""
import json

from app.core.config import settings


def load_policies() -> list[dict]:
    with open(settings.policies_file, encoding="utf-8") as f:
        return json.load(f)["policies"]


def _age_ok(conditions: dict, age: int | None) -> bool:
    if age is None:
        return True  # 나이 미입력 시 일단 후보로 포함 (보수적으로 가정하지 않음)
    return conditions.get("min_age", 0) <= age <= conditions.get("max_age", 200)


def find_eligible_loan_policy(request) -> dict | None:
    """자격 조건을 만족하는 저금리 정책 대출을 찾는다. (버팀목대출 등)"""
    for policy in load_policies():
        if policy["type"] != "LOAN":
            continue
        conditions = policy["conditions"]

        if not _age_ok(conditions, request.age):
            continue
        if request.monthly_income > conditions.get("max_monthly_income", 10_000):
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
            if request.monthly_income > conditions.get("max_monthly_income", 10_000):
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
