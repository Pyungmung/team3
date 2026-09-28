"""
[담당: 송귀성] 정책 자동 추천 API
프론트엔드가 직장 위치 선택지(work hub)를 채우거나, 전체 지원 정책 목록을
보여줄 때 사용하는 보조 엔드포인트.
"""
from fastapi import APIRouter

from app.services import calculator, policy_matcher

router = APIRouter(prefix="/policy", tags=["policy"])


@router.get("/work-hubs")
def list_work_hubs() -> list[str]:
    """현재 MVP가 지원하는 직장 위치(통근 기준점) 목록."""
    return calculator.get_work_hubs()


@router.get("")
def list_policies() -> list[dict]:
    """등록된 전체 청년 주거지원 정책 목록 (조건 포함)."""
    return policy_matcher.load_policies()
