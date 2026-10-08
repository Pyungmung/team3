"""
[담당: 송귀성] 정책 보조 API
프론트엔드가 직장 위치 선택지(work hub)를 채울 때 사용하는 보조 엔드포인트.
(2026-10-08: 전체 지원 정책 목록은 DB로 옮겨져 백엔드의 /api/admin/housing-policies가 맡는다 - 여기서는 더 돌려주지 않는다)
"""
from fastapi import APIRouter

from app.services import calculator

router = APIRouter(prefix="/policy", tags=["policy"])


@router.get("/work-hubs")
def list_work_hubs() -> list[str]:
    """현재 MVP가 지원하는 직장 위치(통근 기준점) 목록."""
    return calculator.get_work_hubs()

