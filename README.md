# 맞집 (CustomHouse) — 청년 맞춤형 주거 케어 플랫폼

> 더 가벼운 주거, 더 나은 내일.
> 월급·보증금·직장 위치를 입력하면 통근 30~40분 이내에서 가장 경제적인 주거지와
> 딱 맞는 청년 주거지원 정책을 추천하는 통합 주거 케어 서비스입니다.

팀 Custom House (SMU 11기 · Team3) — 팀장 송귀성 / 김시연 / 황진구 / 허겸 / 양혜승

## 1. 현재 개발 범위 (MVP)

1차 개발 범위는 **핵심 기능(AI 주거비 절약 추천)** 을 프론트엔드 → 백엔드 → AI 엔진까지
실제로 동작하도록 우선 구현하는 것입니다. 아래는 진행 상태입니다.

| 영역 | 상태 | 담당 |
|---|---|---|
| AI 주거비 절약 진단 (핵심 기능) | ✅ 완료 (샘플 데이터 + MOLIT_API_KEY 있으면 국토부 실거래가 자동 사용) | 송귀성 |
| 프론트 ↔ 백엔드 ↔ AI 엔진 연동 | ✅ 완료 | 송귀성 / 허겸 |
| 공통 인프라 (CORS, 예외처리, `{success,code,message,data}` 응답 규격, `.env` 시크릿 관리) | ✅ 완료 | 허겸 |
| 회원가입/로그인/JWT/네이버 OAuth2 | ✅ 완료 (기본은 H2, 로컬 MySQL 실연동도 검증됨 — `docs/DATABASE.md` 참고. 네이버는 실제 앱 키 등록 필요) | 허겸 |
| 마이페이지 (조건 저장/조회/수정, 알림 수신여부 토글) | ✅ 완료 | 황진구 |
| 토스페이먼츠 결제/구독 (단건 결제, 빌링/자동결제, 매일 09시 스케줄러) | ✅ 완료 (Toss 공개 테스트 키 연동) | 황진구 |
| WatchList / 알림 (등록·조회·삭제, 시세 변동 감지, 허위매물 신고, 알림함) | ✅ 완료 | 김시연 |
| 실제 공공 API 연동 — 국토부 전월세 실거래가 4종(아파트/오피스텔/연립다세대/단독다가구) | ✅ 완료 (실제 서비스키로 검증됨) | 송귀성 |
| 실제 공공 API 연동 — 주택금융공사/부동산원/SGIS/카카오맵 | 🔲 TODO (샘플 JSON 사용 중) | 송귀성 |

각 코드 파일 상단에는 `[담당: 이름]` 주석이 달려 있어 누가 어떤 부분을 이어서
개발하면 되는지 바로 확인할 수 있습니다. 아직 구현되지 않은 폴더에는 `TODO.md`가
대신 들어있습니다.

## 2. 프로젝트 구조

```
team3/
├── frontend/          # HTML, Tailwind CSS, JS (담당: 양혜승)
├── backend/            # Spring Boot & MySQL (담당: 김시연, 황진구, 허겸)
├── customhouse-ai/     # FastAPI / Python — AI·데이터 분석 서버 (담당: 송귀성)
└── docs/               # DB 문서 (담당: 김시연) — DATABASE.md(설명) + DB구조도.html(ERD, 브라우저로 열기)
```

세부 구조는 `frontend/`, `backend/`, `customhouse-ai/` 각 폴더를 참고하세요.
DB 스키마는 [`docs/DATABASE.md`](docs/DATABASE.md)와 [`docs/DB구조도.html`](docs/DB구조도.html)(더블클릭해서
브라우저로 열면 ERD가 보이고, 인쇄/PDF 저장도 가능)에 정리되어 있습니다.

## 3. 실행 방법

세 서버를 각각 다른 터미널에서 띄워야 합니다. (기본 포트: frontend 3000 / backend 8080 / AI 엔진 8000)

### 3-1. AI 엔진 (customhouse-ai, FastAPI) — 먼저 실행

```bash
cd customhouse-ai
cp .env.example .env          # 필요한 키가 있다면 채워넣기 (지금은 비워둬도 정상 동작)
python -m venv .venv
.venv\Scripts\activate        # (Windows PowerShell: .venv\Scripts\Activate.ps1)
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

- 헬스체크: http://localhost:8000/health
- API 문서(Swagger): http://localhost:8000/docs

> ⚠️ 일부 보안 정책이 적용된 Windows PC에서는 numpy/pandas의 네이티브 DLL이
> "애플리케이션 제어 정책"에 의해 차단되어 `import pandas`가 실패할 수 있습니다.
> 이 경우에도 서비스는 정상 동작합니다 — `app/services/data_analysis.py`가
> pandas 로드 실패 시 자동으로 순수 Python 정렬 로직으로 폴백하도록 되어 있습니다.

### 3-2. 백엔드 (backend, Spring Boot)

```bash
cd backend
cp .env.example .env          # DB/JWT/Toss 키가 필요해지면 채워넣기 (지금은 비워둬도 정상 동작)
./mvnw spring-boot:run
```

- Maven Wrapper가 포함되어 있어 별도 Maven 설치가 필요 없습니다 (JDK 21 필요).
- `src/main/resources/application.yml`에서 AI 엔진 주소(`ai-engine.base-url`)를 설정합니다. 기본값은 `http://localhost:8000`.
- `backend/.env`(있다면)는 `spring.config.import`로 자동으로 읽혀 `${DB_PASSWORD}` 등의 플레이스홀더에 주입됩니다.
- 헬스체크: `POST http://localhost:8080/api/recommendation/diagnosis`
- 모든 API 응답은 `{success, code, message, data}` 규격입니다 (`global/common/ApiResponse.java`).

### 3-3. 프론트엔드 (frontend)

빌드 도구 없이 정적 파일입니다. `python -m http.server`는 브라우저가 JS/CSS를 오래 캐시해서
수정사항이 반영 안 되는 것처럼 보일 수 있어, 캐시를 끈 `serve.py`를 대신 사용하세요.

```bash
cd frontend
python serve.py        # http://localhost:3000
```

- 브라우저에서 http://localhost:3000/pages/index.html 접속
- "내 주거비 절약 진단받기" → 조건 입력 → 결과 리포트까지 실제로 동작합니다.
- 회원가입(`pages/auth/signup.html`) → 로그인(`pages/auth/login.html`)도 실제로 동작하며,
  로그인하면 우측 상단이 "로그인" 버튼에서 이메일 + "로그아웃" 버튼으로 바뀝니다.

## 4. 핵심 기능 동작 확인 (curl)

```bash
curl -X POST http://localhost:8080/api/recommendation/diagnosis \
  -H "Content-Type: application/json" \
  -d '{"annualIncome":3400,"deposit":800,"desiredRent":55,"workLocation":"강남구","maxCommuteMinutes":40,"age":28,"noHouseholder":true,"jobType":"SME","preferentialStatuses":[]}'
```

## 5. 회원가입 / 로그인 (curl)

```bash
# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@customhouse.com","password":"password123","nickname":"테스터"}'

# 로그인 (accessToken/refreshToken 발급)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@customhouse.com","password":"password123"}'

# 로그인한 내 정보 조회 (JWT 인증 필요)
curl http://localhost:8080/api/users/me -H "Authorization: Bearer {accessToken}"

# 토큰 재발급
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"{refreshToken}"}'
```

**네이버 소셜 로그인**은 코드는 완성되어 있지만(`/oauth2/authorization/naver`로 이동하면
실제 네이버 로그인 화면으로 리다이렉트됩니다), [네이버 개발자센터](https://developers.naver.com)에서
앱을 등록해 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET`을 발급받아 `backend/.env`에 넣기 전까지는
"등록되지 않은 애플리케이션" 오류가 납니다. 등록 시 콜백 URL은
`http://localhost:8080/login/oauth2/code/naver`로 설정하세요.

## 6. 마이페이지 (curl, JWT 인증 필요)

```bash
# 조회 (저장된 조건 없으면 404 NOT_FOUND)
curl http://localhost:8080/api/mypage/condition -H "Authorization: Bearer {accessToken}"

# 저장/수정 (upsert)
curl -X PUT http://localhost:8080/api/mypage/condition \
  -H "Content-Type: application/json" -H "Authorization: Bearer {accessToken}" \
  -d '{"annualIncome":3400,"workLocation":"강남구","desiredDeposit":1000,"desiredRent":55,"notificationEnabled":true}'
```

프론트엔드에서는 `pages/mypage/index.html`에서 저장된 조건 확인 + 알림 토글,
`pages/mypage/edit-condition.html`에서 저장/수정이 실제로 동작합니다 (로그인 필요).

## 7. 결제/구독 (토스페이먼츠)

`backend/src/main/resources/application.yml`의 `toss.secret-key`/`toss.client-key` 기본값은
[토스페이먼츠 공식 샘플 저장소](https://github.com/tosspayments/tosspayments-sample)에 공개된
**테스트(샌드박스) 키**입니다. 실제 결제는 발생하지 않으며, 별도 가입 없이 바로 동작합니다.
운영 전환 시에만 개발자센터에서 발급받은 라이브 키로 `backend/.env`에서 교체하면 됩니다.

- **단건 결제**: `pages/payment/checkout.html`에서 "카드로 결제하기" → 토스 결제창 → 승인은
  `pages/payment/success.html`이 `POST /api/payments/confirm`으로 서버에 요청해 완료합니다.
- **정기 구독**: "자동결제 수단 등록하기" → 토스 카드 등록창 → `pages/payment/billing-success.html`이
  `POST /api/payments/subscriptions/billing-key`로 빌링키를 발급·저장합니다. 이후
  `SubscriptionScheduler`가 매일 오전 9시 결제 예정일이 된 구독을 자동 청구합니다
  (테스트용으로 결제 페이지의 "지금 바로 구독 결제 실행" 버튼으로 즉시 실행도 가능합니다).
- 백엔드 ↔ Toss 서버 간 실제 통신은 curl로 검증했습니다 (가짜 데이터를 보내면 Toss가 실제
  "존재하지 않는 결제/인증 정보입니다" 같은 응답을 내려줍니다 — 즉 인증과 연동 자체는 정상입니다).
- ⚠️ 실제 결제창(Toss 위젯) 진입까지는 확인했지만, 결제창 내부의 카드 입력까지 끝까지 완료하는
  테스트는 이 환경의 자동화 브라우저에서는 네트워크 샌드박스 제약으로 확인하지 못했습니다.
  실제 브라우저에서 최종 확인해주세요.

```bash
# 구독 상태 조회
curl http://localhost:8080/api/payments/subscriptions/me -H "Authorization: Bearer {accessToken}"

# 구독 해지
curl -X DELETE http://localhost:8080/api/payments/subscriptions -H "Authorization: Bearer {accessToken}"
```

## 8. 관심 매물(WatchList) / 알림 (curl, JWT 인증 필요)

실제 매물 크롤링/외부 API 연동 전이라, 사용자가 직접 매물 정보를 입력해 등록하고
"가격 재확인" 액션으로 시세 변동을 시뮬레이션하는 구조입니다. 변동이 감지되면
그 매물을 지켜보는 사용자 중 마이페이지에서 **알림을 켠 사람에게만** 알림이 발행됩니다.

```bash
# 관심 매물 등록
curl -X POST http://localhost:8080/api/watchlist \
  -H "Content-Type: application/json" -H "Authorization: Bearer {accessToken}" \
  -d '{"address":"서울시 관악구 신림동 123-45","region":"관악구","deposit":1000,"monthlyRent":55}'

# 내 관심 매물 목록
curl http://localhost:8080/api/watchlist -H "Authorization: Bearer {accessToken}"

# 시세 재확인(변동 시 알림 자동 발행) - propertyId는 위 등록 응답의 propertyId
curl -X POST http://localhost:8080/api/watchlist/properties/{propertyId}/recheck-price \
  -H "Content-Type: application/json" -H "Authorization: Bearer {accessToken}" \
  -d '{"deposit":1000,"monthlyRent":65}'

# 허위 매물 신고 (3회 누적 시 지켜보던 사용자에게 주의 알림 발행)
curl -X POST http://localhost:8080/api/watchlist/properties/{propertyId}/report -H "Authorization: Bearer {accessToken}"

# 알림함 조회 / 안읽음 개수 / 읽음 처리
curl http://localhost:8080/api/notifications -H "Authorization: Bearer {accessToken}"
curl http://localhost:8080/api/notifications/unread-count -H "Authorization: Bearer {accessToken}"
curl -X PATCH http://localhost:8080/api/notifications/{id}/read -H "Authorization: Bearer {accessToken}"
```

프론트엔드는 `pages/watchlist/list.html`에서 등록·목록·가격 재확인(테스트)·신고·삭제와
알림함(안읽음 뱃지 포함)이 실제로 동작합니다.

## 9. 실제 공공 API 연동 — 국토교통부 전월세 실거래가 4종

`customhouse-ai/app/services/api_collector.py`가 국토교통부 실거래가 API 4종
(아파트 `RTMSDataSvcAptRent` / 오피스텔 `RTMSDataSvcOffiRent` / 연립다세대 `RTMSDataSvcRHRent` /
단독다가구 `RTMSDataSvcSHRent`)를 실제로 호출합니다. **`DATA_GO_KR_API_KEY`를 안 채우면
샘플 데이터(`regions.json`)로 자동 폴백**하고, 채우면 최근 3개월 실거래 개별 건이 후보 매물로
반영됩니다. 진단 결과의 각 매물에 `data_source`("국토부 실거래가 (YYYY.M 거래)" 또는 "샘플 데이터")와
`property_type`(주거 유형)이 표시됩니다. 4종 모두 같은 키 하나로 호출됩니다.

- 단독다가구는 응답에 건물명·층이 없어 "역삼동 다가구"처럼 동네+형태로 표시하고, 면적은 연면적입니다.
- 지역·유형·월별 호출은 동시에(스레드풀) 보내고, 일부가 실패한 지역 결과는 캐시하지 않습니다
  (성공 결과는 6시간 캐시). 서버를 막 켠 직후 첫 진단만 몇 초 걸립니다.

**키 발급 방법**: [data.go.kr](https://www.data.go.kr) 회원가입 → "전월세 실거래가" 검색 →
국토교통부 아파트/오피스텔/연립다세대/단독다가구 전월세 자료 활용신청 (보통 즉시 승인) →
마이페이지 > 개발계정에서 "일반 인증키(Decoding)" 복사 → `customhouse-ai/.env`에
`DATA_GO_KR_API_KEY=`로 붙여넣기.

```bash
# 연동 확인 (data_source / property_type 필드로 실거래가 사용 여부와 유형 확인)
curl -X POST http://localhost:8000/api/v1/diagnosis -H "Content-Type: application/json"   -d '{"annualIncome":3400,"deposit":800,"workLocation":"강남구"}'   | python -c "import sys,json; d=json.load(sys.stdin); [print(r['region'], r['property_type'], r['data_source']) for r in d['wolse_recommendations']+d['jeonse_recommendations']]"
```

> 한국주택금융공사, 한국부동산원, SGIS API는 아직 미연동입니다 (`.env.example`에 키 자리만 준비되어 있음).
> 관리비(`regions.json`의 지역 평균)와 통근시간(직선거리 추정)은 아직 실데이터가 아닌 샘플/추정치입니다.

## 10. 알아두어야 할 것 (샘플 데이터 안내)

- `customhouse-ai/app/data/regions.json`, `policies.json`은 **예시(샘플) 데이터**입니다.
  위 9번 항목처럼 실거래가 API로 순차 교체 중입니다.
- 현재 직장 위치(통근 기준점)는 서울 25개 자치구 전체 + 분당구를 지원하며, 카카오 주소검색으로
  입력하면 구 단위 대표좌표 대신 실제 주소 좌표로 통근시간을 계산합니다(`work_locations.json`).
- 진단 기능은 로그인 없이도 사용할 수 있습니다 (`SecurityConfig`에서 `/api/recommendation/**`는 permitAll).
- `CLAUDE.md`에는 프론트엔드 목표 스택이 React로 되어 있지만, 지금은 빠른 검증을 위해
  순수 HTML/CSS/JS로 구현되어 있습니다. React 전환은 다음 단계 작업입니다.
