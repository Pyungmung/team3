"""
[담당: 송귀성] FastAPI 환경설정 (Config)
CORS 허용 origin, 데이터 파일 경로, 공공 API 키 등 앱 전역 설정을 관리한다.

CLAUDE.md 수칙: 키는 .env에만 보관(Git/채팅 절대 금지), .env.example로 견본 제공.
.env 파일이 있으면 자동으로 읽어온다 (없어도 정상 동작 - 지금은 샘플 데이터만 쓰므로 선택 입력).

주의: env_file은 반드시 절대경로로 지정한다. 상대경로("​.env")를 쓰면 uvicorn을 실행한 프로세스의
현재 작업 디렉터리(cwd) 기준으로 찾는데, --app-dir customhouse-ai로 실행해도 uvicorn은
sys.path에만 추가할 뿐 실제로 chdir하지 않는다. 그래서 프로젝트 루트에서 실행하면 .env를
못 찾아 키가 전부 비어있는 것처럼 동작하고(국토부 실거래가 등이 조용히 샘플 데이터로 폴백),
customhouse-ai 폴더 안에서 실행하면 정상 동작하는 등 실행 위치에 따라 결과가 달라지는
문제가 있었다 (2026-09-27 발견).
"""
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",  # .env에 아직 안 쓰는 키가 있어도 에러 없이 무시
    )

    app_name: str = "customhouse-ai"

    # 백엔드(Spring Boot)에서의 호출을 허용하기 위한 CORS 설정.
    # 배포 시에는 실제 백엔드 도메인으로 제한할 것.
    allowed_origins: list[str] = ["*"]

    # 샘플 데이터 경로. data_go_kr_api_key가 없으면(또는 API 호출 실패 시) 이 파일로 폴백한다.
    data_dir: Path = Path(__file__).resolve().parent.parent / "data"
    regions_file: Path = data_dir / "regions.json"
    policies_file: Path = data_dir / "policies.json"
    lawd_codes_file: Path = data_dir / "lawd_codes.json"
    region_coords_file: Path = data_dir / "region_coords.json"
    work_locations_file: Path = data_dir / "work_locations.json"

    # 더미 매물 CSV 폴더 (dummyhouse_(자치구영문).csv 25개). 추천 매물(listing_recommender.py)과 신규 매물
    # 등록(listing_repository.append_listing)이 같은 파일을 읽고 쓴다. 배포처럼 저장소 구조와 다르면
    # DUMMY_HOUSES_DIR 환경변수(.env)로 경로를 덮어쓴다.
    dummy_houses_dir: Path = Path(__file__).resolve().parents[3] / "docs" / "samples" / "dummyhouses"

    # --- 공공/외부 API 키 (.env.example 참고) ---
    # data.go.kr(공공데이터포털)은 계정 1개당 인증키 1개를 발급하고, 그 키로 여러 API를
    # 공용으로 쓸 수 있다. 아래 2개(국토교통부 전월세 실거래가 / 한국주택금융공사)는 전부
    # data.go.kr에 올라와 있는 API라 같은 키 하나로 통일했다.
    # 주의: "행정안전부_법정동코드" API도 data.go.kr에 있지만 이건 법정동코드<->지역명 코드표
    # 조회용이라 주소를 만들어주지 않는다 (지금은 app/data/lawd_codes.json에 고정값으로 박아뒀다).
    # 도로명주소가 필요하면 완전히 별도 기관 API인 JUSO_API_KEY(아래)를 써야 한다.
    data_go_kr_api_key: str | None = None  # 국토교통부 실거래가(구현됨) + 주택금융공사(TODO)에서 공용으로 사용

    reb_api_key: str | None = None         # 한국부동산원 (data.go.kr 소속이 아니라 별도 발급)
    sgis_api_key: str | None = None        # SGIS 통계지리정보서비스 (Open API 인증키 1개만 발급됨, 별도 시크릿 없음)
    kakao_map_app_key: str | None = None   # 카카오맵
    kakao_rest_app_key: str | None = None  # 카카오모빌리티 길찾기(자동차) REST API - services/kakao_mobility.py
    juso_api_key: str | None = None        # 행정안전부 도로명주소 검색 API(business.juso.go.kr) - services/juso_api.py
    juso_api_key_av: str | None = None     # 행정안전부 상세주소 API - 위와 별도 발급/승인키 (건물명 보완용)
    pinecone_api_key: str | None = None    # Pinecone (RAG, 심화 단계). 서버리스 API라 environment 값은 불필요.


settings = Settings()
