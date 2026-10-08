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

- 브라우저에서 http://localhost:3000/ 접속 (2026-09-29: 메인 홈페이지가 `frontend/index.html`로 옮겨져 루트에서 바로 뜬다.
  다른 페이지들은 여전히 `http://localhost:3000/src/pages/...`)
- "내 주거비 절약 진단받기" → 조건 입력 → 결과 리포트까지 실제로 동작합니다.
- 회원가입/로그인은 `pages/auth/signup.html` 한 화면에서 탭으로 전환되며(`login.html`은 그 화면의 로그인 탭으로 연결) 실제로 동작하고,
  로그인하면 우측 상단이 "로그인" 버튼에서 이메일 + "로그아웃" 버튼으로 바뀝니다.

## 4. 핵심 기능 동작 확인 (curl)

```bash
curl -X POST http://localhost:8080/api/recommendation/diagnosis \
  -H "Content-Type: application/json" \
  -d '{"annualIncome":3400,"deposit":800,"desiredRent":55,"workLocation":"강남구","maxCommuteMinutes":40,"age":28,"noHouseholder":true,"jobType":"SME","preferentialStatuses":[]}'
```

위 `/diagnosis`는 국토부 실거래가 기준 리포트(`report-result.html`)용이고, **더미 매물 기반 추천 리포트**(`report-listings.html`, 진단의 기본 화면)는
같은 요청 본문으로 `POST /api/recommendation/diagnosis/listings`(AI 엔진 `POST /api/v1/diagnosis/listings`)를 호출합니다.

- 더미 매물 데이터: `docs/samples/dummyhouses/dummyhouse_(자치구영문).csv` 25개(서초구 4,000건 + 나머지 구 각 3,000건, 54컬럼).
  AI 엔진 경로는 `.env`의 `DUMMY_HOUSES_DIR`로 바꿀 수 있습니다 (`app/core/config.py`의 `dummy_houses_dir`).
- 스키마/저장소: `customhouse-ai/app/services/listing_schema.py`(컬럼 단일 출처), `listing_repository.py`(읽기 + 신규 매물 `append_listing`),
  `listing_builder.py`(행안부 도로명주소·카카오 좌표·국토부 실거래 조회, 신규 매물 생성 탭에서 재사용).
- CSV 재생성: `cd customhouse-ai && python scripts/gen_dummyhouses.py` (작업 파일은 `scripts/_work/`, Git 제외).

### 추천 리포트 부가 기능 - pull 받은 뒤 구동 확인 (2026-09-28)

`.env`/`config.js`가 없는 새 클론에서도 아래 기능은 키 없이 동작하도록 확인했습니다 (키가 있으면 더 정확해질 뿐입니다).

| 기능 | 필요한 것 | 키/설정이 없으면 |
|---|---|---|
| 매물 추천·지도 카드 | 저장소에 포함된 CSV(`docs/samples/dummyhouses`) | 카카오 REST 키가 없으면 통근시간을 직선거리 추정으로 계산 ("직선거리 추정" 배지), 카카오맵 JS 키(`frontend/src/js/config.js`)가 없으면 지도만 비어 있고 목록은 정상 |
| 소득 대비 주거비 비율(RIR)·적정 월세 | `docs/RIR.csv` (포함됨) | 파일을 못 읽으면 기본 20% |
| 보증금전환 실질거주비 | `REB_API_KEY` (한국부동산원) | 키가 없으면 저장값/기본 6.35% 사용 |
| 주거정책 추천(자치구별) | DB `housing_policies` (서버 최초 기동 때 `backend/src/main/resources/seed/housing_policies.json`으로 시드, 이후 **관리자 수정 > 주거지원정책**에서 관리) | 정책을 못 읽으면 추천 표가 비어 보임 |
| 정책정보 정정신고 / 허위매물 신고 메일 | `backend/.env`의 `MAIL_USERNAME`, `MAIL_APP_PASSWORD` | 정정신고는 발송 실패 안내, 허위매물 신고는 **신고 저장은 되고** 메일만 서버 로그에 실패로 남음 |
| 허위매물 신고 / 관심매물(하트) | 로그인(회원가입은 이메일 인증 메일이 필요) | 비로그인이면 로그인 안내 팝업 |

- **전세자금대출 자격 표시**: 관리자 계정("관리자 수정" 탭)이 저장한 대출 조건(`loan_products`)을 백엔드가 AI 엔진 요청에 실어 보내고, AI 엔진(`customhouse-ai/app/services/loan_matcher.py`, 판별 규칙은 `policy_matcher.py`와 같음)이 매물마다 신청 가능한 대출을 판별해 카드에 "[대출이름] 신청 가능"으로 표시합니다. 전세 매물에는 전세 대출, 월세 매물에는 청년전용 보증부월세대출만 붙고, 저장하지 않은 대출·"정책 대출 활용" 해제 시에는 표시되지 않습니다.
카드에는 "[대출이름] 실질주거비"(월세+관리비+보증금 대출이자)도 함께 표시됩니다 — 2026-09-29 기준 실제 대출별 금리표가 아직 없어
5개 대출 공통 임시 기본금리 3%(`customhouse-ai/app/services/loan_matcher.py`의 `DEFAULT_BASE_RATE_PERCENT`)에서 사용자가 해당하는
우대사항의 우대금리만 뺀 참고용 값입니다(우대금리 차감은 그 우대사항이 "필수"가 아니어도 해당하면 적용되고, 자격 판별과는 별개입니다).
표 이미지를 보고 실제 금리표를 반영하면 이 값이 대체됩니다. 관리자 화면을 쓰려면 `backend/.env`의 `ADMIN_PASSWORD`가 필요합니다.
AI 엔진 테스트: `cd customhouse-ai && python tests/test_loan_matcher.py`, `python tests/test_kakao_quota_guard.py`.
- **주거정책(리포트 "주거정책 추천")은 DB로 관리합니다 (2026-10-08).** 예전처럼 CSV를 고치고 변환 스크립트를 돌릴 필요가 없습니다.
  관리자 계정 > **관리자 수정 > 주거지원정책** 탭에서 서울공통 + 25개 자치구별로 정책을 한 줄씩 수정/추가/삭제하면 다음 진단부터 바로 반영됩니다
  (나이·연소득·총자산·기준중위소득%는 비우면 "제한 없음", 기초수급/중소기업/신혼부부/무주택/대출은 체크 칸, 링크는 `http://`·`https://`로 시작하는 안내 페이지 주소 - 비우면 [이동] 버튼이 회색 비활성).
  정책을 수정/삭제하면 그 정책을 **관심정책으로 담은 회원의 알림함**에 알림이 가고, 삭제된 정책은 관심정책에서도 빠집니다.
  예전 `docs/housing_policy_list.csv`와 변환 스크립트는 삭제했습니다 (최초 시드 파일은 `backend/src/main/resources/seed/housing_policies.json`, CSV 원본은 git 기록에 남아 있습니다).
- 새 테이블 `listing_reports`, `listing_favorites`, `housing_policies`, `favorite_policies`는 dev(H2)/local-mysql에서는 서버를 켜면 자동 생성됩니다. 운영(`ddl-auto: validate`)은 수동 DDL이 필요합니다 (`docs/DATABASE.md` 참고).
- 로컬에서 메일 없이 기능을 확인하려면 받은 메일을 파일에 저장만 하는 가짜 SMTP 서버(로컬 25xx 포트)를 두고 `SPRING_MAIL_HOST/PORT`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false` 환경변수로 백엔드를 띄우면 실제 메일이 나가지 않습니다.

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

### 광고하기 (단건결제 1,990원, 2026-10-08)

매물 등록자가 결제하면 그 매물이 추천 리포트의 일반 추천 카드 **5개마다 그 다음 칸**(#5 다음, #10 다음 …)에 "광고하기 매물"로 끼어 보입니다.
일반 추천 순번(#N)은 그대로고, 광고 카드는 순번 대신 "광고하기 매물" 배지가 붙습니다 (지도 핀에는 올리지 않음).
광고는 자리마다 광고 풀에서 무작위로 뽑고, 광고가 자리보다 적으면 풀을 다시 섞어 이미 나온 광고도 반복해 올립니다(목록 끝까지 5의 배수 다음 자리는 항상 광고, 같은 광고가 연달아 나오지 않음).

- **신청 경로**: 매물 등록 화면의 "📢 광고하기 매물등록"(결제 후 매물 등록 + 광고 접수) / 마이페이지 > 등록한 매물 관리의 "📢 광고하기"(이미 등록한 매물, 광고 중이면 "광고 연장").
- **흐름**: `POST /api/ads/orders`(서버가 가격·기간을 정해 주문 생성) → `pages/payment/ad-checkout.html`(토스 결제) → `pages/payment/ad-success.html`이 `POST /api/ads/confirm`(결제 승인 + 매물 등록 + 광고 접수). 승인 뒤 처리에 실패하면 결제는 자동 취소됩니다.
- **노출 조건**: 광고 매물이 사용자의 **직장 자치구**에 있고, 그 매물 자신의 통근시간(직선거리 추정)이 희망 통근시간 이내이며, 보증금이 보증금 한도 이하인 사용자에게만 보입니다. 추천 순위·유형 필터와는 무관하게 무작위 순서입니다.
- **관리자**: 관리자 수정 > 기타 설정에서 가격(기본 1,990원)·노출 기간(기본 30일)을, > 광고 현황에서 주문 목록과 환불(토스 결제 취소)을 관리합니다.
- **키**: 클라이언트 키는 코드에 넣지 않고 서버(`TOSS_CLIENT_KEY`)가 내려줍니다. `backend/.env`(로컬)와 Render 환경변수(배포)에 `TOSS_SECRET_KEY`/`TOSS_CLIENT_KEY`를 **같은 종류의 키 쌍**으로 넣으세요.
  결제위젯 연동 키(`test_gck_`/`test_gsk_`)면 결제 수단 선택 위젯이, 일반 API 개별 연동 키(`test_ck_`/`test_sk_`)면 결제창이 열립니다. 테스트 키로는 실제 결제가 되지 않습니다.

```bash
# 구독 상태 조회
curl http://localhost:8080/api/payments/subscriptions/me -H "Authorization: Bearer {accessToken}"

# 구독 해지
curl -X DELETE http://localhost:8080/api/payments/subscriptions -H "Authorization: Bearer {accessToken}"
```

### 배너 광고 + 구글 광고 자리 (2026-10-08)

생활형 상품(이사 / 인터넷 / 청소 / 생필품) 광고를 페이지 어디든 붙일 수 있는 **광고 자리**입니다. 아직 어느 페이지에도 넣지 않았고, 자리만 정해지면 한 줄이면 됩니다.

```html
<div data-ad-slot="home-bottom" data-ad-kind="both" data-ad-size="banner"></div>
<!-- 페이지 하단에 스크립트 (순서 중요): config.js → auth.js → address-match-util.js → mypage.js → ad-targeting.js → ad-slot.js -->
```

- `data-ad-kind`: `direct`(직접 배너만) / `google`(구글 AdSense만) / `both`(기본: 직접 배너 우선, 맞는 배너가 없으면 구글 광고). 보여 줄 광고가 없으면 자리가 접혀 빈 공간이 생기지 않습니다. 모든 배너에는 "광고" 표시가 붙고 링크는 새 탭(`rel="sponsored noopener nofollow"`)으로 열립니다. 노출·클릭은 집계하지 않습니다.
- **직접 광고(우리가 사진·링크를 올림)**: 관리자 수정 > **배너 광고** 탭에서 배너를 등록합니다(사진 업로드, 링크, 카테고리, 노출 조건, 기간, 자리 이름). 방문자 조건에 맞는 배너 중 무작위 1개를 보여 줍니다.
  - **월세 성향**: 방문자가 입력한 최대 매물 월세 ≤ 적정 월세 상한(소득 × 수도권 RIR)이면 **절약형**(최저가가 강점인 서비스), 초과면 **프리미엄형**(프리미엄 서비스). **최대 월세를 비웠거나 로그인하지 않았으면 성향 구분 없이 랜덤**(모든 배너가 후보).
  - 직장 자치구, 이사 일정(1개월 이내 / 3개월 이내까지), 광고 자리 이름은 선택 조건이며 비우면 제한 없음입니다. 관리자 탭의 "방문자 조건별 미리보기"로 조건마다 어떤 배너가 나오는지 확인할 수 있습니다.
- **구글 광고(AdSense)**: Google Ads API는 광고주용이라 쓰지 않습니다. AdSense는 키가 아니라 **게시자 ID와 광고 단위 ID**를 쓰고, 어떤 광고가 나올지는 구글이 정합니다(원치 않는 업종은 AdSense 화면에서 차단).
  1. AdSense 가입 → 사이트 심사 통과 → 게시자 ID(`ca-pub-숫자`)와 광고 단위 ID(숫자) 확인, `ads.txt` 게시
  2. Vercel 환경변수 `ADSENSE_CLIENT`(게시자 ID), `ADSENSE_SLOT`(기본 광고 단위 ID) 입력 후 재배포 (`frontend/generate-config.js`가 `config.js`에 넣음. 로컬은 `config.js`에 직접). 자리마다 다른 광고 단위는 `data-adsense-slot`
  3. 설정 전에는 구글 광고 자리가 아무것도 그리지 않습니다. 개인정보 처리방침에 광고·쿠키 안내 문구 추가가 필요합니다.

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
샘플 데이터(`regions.json`)로 자동 폴백**하고, 채우면 최근 2개월 실거래 개별 건이 후보 매물로
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

- `customhouse-ai/app/data/regions.json`은 **예시(샘플) 데이터**입니다.
  위 9번 항목처럼 실거래가 API로 순차 교체 중입니다.
- 현재 직장 위치(통근 기준점)는 서울 25개 자치구 전체 + 분당구를 지원하며, 카카오 주소검색으로
  입력하면 구 단위 대표좌표 대신 실제 주소 좌표로 통근시간을 계산합니다(`work_locations.json`).
- 진단 기능은 로그인 없이도 사용할 수 있습니다 (`SecurityConfig`에서 `/api/recommendation/**`는 permitAll).
- `CLAUDE.md`에는 프론트엔드 목표 스택이 React로 되어 있지만, 지금은 빠른 검증을 위해
  순수 HTML/CSS/JS로 구현되어 있습니다. React 전환은 다음 단계 작업입니다.
