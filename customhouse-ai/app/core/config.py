"""
[담당: 송귀성] FastAPI 환경설정 (Config)
CORS 허용 origin, 데이터 파일 경로, 공공 API 키 등 앱 전역 설정을 관리한다.

CLAUDE.md 수칙: 키는 .env에만 보관(Git/채팅 절대 금지), .env.example로 견본 제공.
.env 파일이 있으면 자동으로 읽어온다 (없어도 정상 동작 - 지금은 샘플 데이터만 쓰므로 선택 입력).
"""
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",  # .env에 아직 안 쓰는 키가 있어도 에러 없이 무시
    )

    app_name: str = "customhouse-ai"

    # 백엔드(Spring Boot)에서의 호출을 허용하기 위한 CORS 설정.
    # 배포 시에는 실제 백엔드 도메인으로 제한할 것.
    allowed_origins: list[str] = ["*"]

    # 샘플 데이터 경로. molit_api_key가 없으면(또는 API 호출 실패 시) 이 파일로 폴백한다.
    data_dir: Path = Path(__file__).resolve().parent.parent / "data"
    regions_file: Path = data_dir / "regions.json"
    policies_file: Path = data_dir / "policies.json"
    lawd_codes_file: Path = data_dir / "lawd_codes.json"

    # --- 공공/외부 API 키 (.env.example 참고) ---
    molit_api_key: str | None = None       # 국토교통부 전월세 실거래가 (services/api_collector.py에서 사용)
    hf_api_key: str | None = None          # 한국주택금융공사
    reb_api_key: str | None = None         # 한국부동산원
    sgis_api_key: str | None = None        # SGIS 통계지리정보서비스
    sgis_api_secret: str | None = None
    juso_api_key: str | None = None        # 행정안전부 법정동코드
    kakao_map_app_key: str | None = None   # 카카오맵
    pinecone_api_key: str | None = None    # Pinecone (RAG, 심화 단계)
    pinecone_environment: str | None = None


settings = Settings()
