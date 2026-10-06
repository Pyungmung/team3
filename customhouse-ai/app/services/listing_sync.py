"""
[담당: 송귀성] 회원 등록 매물 복원 (2026-10-06)

회원이 등록한 매물의 원본은 백엔드 DB(registered_listings.row_json)에 있고, 이 엔진의 CSV는 사본이다.
Render 무료 서버는 재배포하거나 한동안 쉬었다 켜질 때 CSV가 저장소의 원래 상태로 돌아가 등록 매물이 사라지므로,
서버가 켜질 때마다(main.py의 lifespan) 백엔드의 내부 API(GET /api/internal/registered-listings)에서
등록 매물 행을 받아 CSV에 되살린다 (listing_repository.upsert_rows - 이미 있는 매물은 건드리지 않는다).

- 백엔드도 자고 있을 수 있어서(깨어나는 데 20~50초) 백그라운드 스레드에서 몇 번 재시도하고, 그동안 요청 처리는 막지 않는다.
- BACKEND_BASE_URL과 INTERNAL_API_KEY가 둘 다 설정돼 있어야 동작한다 (로컬 개발에서는 건너뜀).
- 복원에 실패해도 엔진은 평소처럼 동작한다 (등록 매물만 안 보일 뿐).
"""
import logging
import threading
import time

import requests

from app.core.config import settings
from app.services import listing_repository

logger = logging.getLogger(__name__)

INTERNAL_PATH = "/api/internal/registered-listings"
RETRY_COUNT = 12          # 15초 간격으로 12번 = 최대 약 3분 (백엔드가 깨어나는 시간)
RETRY_INTERVAL_SECONDS = 15
REQUEST_TIMEOUT_SECONDS = 60


def is_configured() -> bool:
    return bool(settings.backend_base_url and settings.internal_api_key)


def fetch_rows() -> list[dict]:
    """백엔드에서 회원 등록 매물 행(CSV 헤더 -> 값) 전부를 받아 온다."""
    url = settings.backend_base_url.rstrip("/") + INTERNAL_PATH
    r = requests.get(url, headers={"X-Internal-Key": settings.internal_api_key}, timeout=REQUEST_TIMEOUT_SECONDS)
    r.raise_for_status()
    body = r.json()
    data = body.get("data") if isinstance(body, dict) else None
    if not isinstance(data, list):
        raise RuntimeError("내부 API 응답 형식이 올바르지 않습니다")
    return [row for row in data if isinstance(row, dict)]


def sync_once() -> dict:
    """한 번 받아서 CSV에 되살린다. 설정이 없으면 건너뛴다. 반환: upsert_rows 결과(+ fetched 수)."""
    if not is_configured():
        return {"added": 0, "skipped": 0, "fetched": 0, "configured": False}
    rows = fetch_rows()
    result = listing_repository.upsert_rows(rows)
    result.update({"fetched": len(rows), "configured": True})
    return result


def sync_with_retry(retries: int = RETRY_COUNT, interval: float = RETRY_INTERVAL_SECONDS, sleep=time.sleep) -> bool:
    """성공할 때까지(최대 retries번) 재시도한다. 성공하면 True."""
    for attempt in range(1, retries + 1):
        try:
            result = sync_once()
            if not result["configured"]:
                logger.info("등록 매물 복원 건너뜀: BACKEND_BASE_URL/INTERNAL_API_KEY 미설정")
                return False
            logger.info(f"등록 매물 복원 완료: 받은 {result['fetched']}건, 새로 넣은 {result['added']}건, 이미 있어 건너뜀 {result['skipped']}건")
            return True
        except Exception as e:  # noqa: BLE001 - 어떤 실패든 엔진 동작을 막지 않는다
            # 요청 주소/키가 예외 문구에 섞일 수 있어 키를 가려서 남긴다
            detail = str(e).replace(settings.internal_api_key or "<NO-KEY>", "<KEY>")[:150]
            logger.warning(f"등록 매물 복원 실패 ({attempt}/{retries}): {type(e).__name__}: {detail}")
            if attempt < retries:
                sleep(interval)
    return False


def start_background_sync() -> threading.Thread | None:
    """서버가 켜질 때 호출: 설정이 있으면 백그라운드 스레드에서 복원을 시작한다."""
    if not is_configured():
        logger.info("등록 매물 복원 건너뜀: BACKEND_BASE_URL/INTERNAL_API_KEY 미설정")
        return None
    thread = threading.Thread(target=sync_with_retry, name="listing-sync", daemon=True)
    thread.start()
    return thread
