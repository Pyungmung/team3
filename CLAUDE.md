\# 프로젝트: 맞집 (청년 맞춤형 주거비 절약 웹앱)



\## 스택 \& 포트

\- FE (3000, 목표): React, Tailwind CSS (sm/md/lg 반응형)

\- FE 현재 상태: MVP 1차는 `frontend/` 하위에 순수 HTML/CSS/바닐라 JS + Tailwind CDN으로 구현됨 (포트 3000, `python frontend/serve.py`). 핵심 기능(AI 주거비 진단)과 회원가입/로그인/로그아웃이 백엔드·AI 엔진과 실제로 연동되어 동작 확인됨. React 전환은 다음 단계 작업.

\- BE (8080): Spring Boot (Java), Spring Security (JWT) 완료 / DB는 현재 H2 인메모리(개발용), MySQL은 운영 전환 시 적용 예정 (`application-prod.yml`)

\- AI (8000): Python, Pandas, Pinecone (RAG), 공공 API



\## 브랜치 \& 개발 수칙

\- 브랜치: `develop` (통합/테스트) / `<파트>-<이름>` (작업 후 `develop` 병합)

\- 수칙: 복잡한 구현은 Plan 모드 선탐색, 코드 작성 후 빌드/테스트 검증

\- 시크릿: `.env`에 키 보관(Git/채팅 절대 금지), `.env.example` 견본 제공



\## 아키텍처 \& 인증

\- 통신: FE ↔ BE ↔ AI (REST API)

\- BE 응답: `ApiResponse<T>` (`{success, code, message, data}`) \& `CustomException` 전역 예외

\- 인증: JWT (로그인 시 발급 → `localStorage` 저장 → `Authorization` 헤더 전송) — 구현 완료. 이메일 회원가입/로그인/토큰재발급 + 네이버 OAuth2(코드 완성, 실 서비스 등록 전까지는 더미 키로 기동)



\## AI 로직 \& 공공 API

\- 산출식: 실질 주거비 = 월세 + 관리비 + 대출이자 + 교통비 - 정부지원금

\- 연동 API: 국토부 전월세 실거래가(아파트/오피스텔/연립다세대/단독다가구), 주택금융공사 대출금리·보증추천, 한국부동산원, SGIS, 법정동코드, 카카오맵



\## TBD (추후 갱신)

\- 운영용 MySQL DB 위치 및 접속 정보 (현재 개발은 H2 인메모리) / 관리비·교통비 공공데이터 출처

\- 네이버 개발자센터 앱 등록 (client-id/secret 발급 후 backend/.env에 설정)

\- FE/BE/AI 설치·빌드·테스트 명령어 / `CustomException` 에러 코드 체계 상세 (1차 초안: `backend/src/main/java/com/customhouse/global/error/ErrorCode.java`)

\- HTML 프로토타입 → React 전환 시점 및 범위

