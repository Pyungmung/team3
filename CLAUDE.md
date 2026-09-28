# 맞집 (청년 주거비 절약 웹앱)

**스택**: FE 3000(순수 HTML/JS+Tailwind CDN, `python frontend/serve.py`, React 전환 예정) · BE 8080(Spring Boot+JWT, dev=H2/prod=MySQL) · AI 8000(Python/FastAPI)

**규칙**: 작업 브랜치는 `claude-mvp`. `develop`/`main`엔 확인 없이 push 금지. 시크릿은 `.env`만 사용(Git/채팅 금지, 안 쓰는 키는 주석 처리). 새 파일 상단에 `[담당: 이름]` 주석. 복잡한 구현은 Plan 모드로 먼저 계획 후 빌드/테스트.

**아키텍처**: 응답은 `ApiResponse<T>`+`CustomException`. JWT는 로그인 시 발급→localStorage→Authorization 헤더. 도메인 간 참조는 FK 없이 `Long id`(board/mypage 하위 테이블만 예외, cascade 사용).

**상세 문서**: DB는 `docs/DATABASE.md`+`docs/DB구조도.html`, 정책 데이터는 `customhouse-ai/app/data/policies.json` 참고.
