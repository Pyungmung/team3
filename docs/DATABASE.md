# 맞집 데이터베이스 가이드 (담당: 김시연)

이 문서는 DB 담당자(김시연)가 스키마를 빠르게 파악하고 안전하게 수정할 수 있도록 정리한 문서입니다.
구조도(그림)는 [`DB구조도.html`](DB구조도.html)에 따로 있습니다 — 브라우저로 열어서 보거나 인쇄(Ctrl+P)할 수 있습니다.

> ✅ 2026-09-15 로컬 MySQL(8.0) 실연동 확인 완료 — 8개 테이블 전부 `engine=InnoDB`로 생성되고
> 회원가입 → 마이페이지 저장 → 관심 매물 등록까지 실제 데이터가 정상적으로 쌓이는 것까지 검증했습니다.

## 1. 지금 뭘 쓰고 있나?

| 환경 | DB | 데이터 유지 여부 | 활성화 방법 |
|---|---|---|---|
| 기본 개발(dev) | **H2 인메모리** | ❌ 서버 재시작하면 초기화 | 그냥 `./mvnw spring-boot:run` (기본값) |
| 로컬 MySQL 테스트 | **MySQL** (내 PC에 설치된 것) | ✅ 유지됨 | 아래 "로컬 MySQL로 실행하기" 참고 |
| 운영(prod) | **MySQL** (서버) | ✅ 유지됨 | 배포 시 자동 |

**왜 기본값이 H2인가요?** 팀원 전체가 MySQL을 설치하지 않아도 `git clone` 후 바로 백엔드를 실행해볼 수
있게 하기 위해서입니다. 테이블 구조(엔티티)는 H2든 MySQL이든 완전히 동일하게 적용되므로,
평소엔 H2로 빠르게 개발하고 실제 데이터가 쌓이는 걸 눈으로 보고 싶을 때만 MySQL로 전환하면 됩니다.

### 로컬 MySQL로 실행하기

이름은 "local-mysql"이지만 `DB_HOST`를 채우기 나름이라 내 PC에 설치된 MySQL뿐 아니라 Aiven 같은
클라우드 MySQL에도 이 프로필 하나로 붙을 수 있습니다.

1. `backend/.env` 파일을 열어서 (없으면 `backend/.env.example`을 복사) `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD`를
   본인 MySQL 접속 정보로 채웁니다. **비밀번호는 여기(.env)에만 적고, 깃/채팅에는 절대 올리지 마세요.**
   - **클라우드 MySQL(Aiven 등)이면 `DB_PORT`를 반드시 채워야 합니다.** 클라우드 서비스는 3306이 아니라
     서비스별로 다른 포트를 쓰기 때문에, 안 채우면 3306으로 접속을 시도하다가 TCP 연결 타임아웃이 납니다
     (콘솔의 접속 정보에서 포트 번호를 확인하세요). 콘솔에 나온 기본 데이터베이스 이름이 "customhouse"가
     아니면(예: Aiven 기본값 `defaultdb`) `DB_NAME`도 함께 채우세요.
   - 로컬 PC에 설치한 MySQL이면 `DB_PORT`/`DB_NAME`은 기본값(3306/customhouse) 그대로 둬도 됩니다.
2. `dev`와 `local-mysql` 프로필을 함께 켜서 실행합니다 (dev의 나머지 설정 + MySQL 접속 정보를 같이 적용):

   ```powershell
   # PowerShell
   cd backend
   $env:SPRING_PROFILES_ACTIVE = "dev,local-mysql"
   .\mvnw.cmd spring-boot:run
   ```

3. `customhouse`라는 데이터베이스가 없으면 자동으로 만들어지고(`createDatabaseIfNotExist=true`),
   엔티티에 정의된 테이블도 자동으로 생성/갱신됩니다 (`ddl-auto: update`).
4. MySQL Workbench 등으로 `customhouse` 데이터베이스를 열어서 실제 테이블을 확인할 수 있습니다.

> 설정 파일은 `backend/src/main/resources/application-local-mysql.yml` 입니다.

## 2. 테이블 목록 (2025-09 기준)

기획서에서 정의했던 5개 테이블(`properties`, `registry_logs`, `registry_analysis`, `favorites`,
`notifications`) 중 `registry_analysis`를 제외한 전부가 구현되어 있고, 다른 도메인(회원/결제/마이페이지)
테이블도 함께 붙었습니다. 실제 엔티티 코드가 항상 정답이니, 이 문서와 다르면 코드를 기준으로 삼으세요.

| 테이블 | 엔티티 파일 | 담당 | 한 줄 설명 |
|---|---|---|---|
| `users` | `domain/user/entity/User.java` | 허겸 | 회원 (이메일 가입 / 네이버 소셜) |
| `housing_conditions` | `domain/mypage/entity/HousingCondition.java` | 황진구 | 마이페이지에 저장한 주거 조건 + 알림 수신 여부 |
| `payments` | `domain/payment/entity/Payment.java` | 황진구 | 안심 매물 리포트 단건 결제 이력 |
| `subscriptions` | `domain/payment/entity/Subscription.java` | 황진구 | 맞집 프리미엄 월 구독(빌링) 상태 |
| ~~`properties`~~ | (삭제됨) | 김시연 | 예전 "관심 매물 직접 등록" 방식의 매물 정보. 2026-10-05 코드 삭제, 테이블은 남아 있어도 안 쓴다 |
| ~~`favorites`~~ | (삭제됨) | 김시연 | 예전 관심 매물(사용자 ↔ 매물). 2026-10-05 코드 삭제 - 지금 관심매물은 `listing_favorites`(하트) |
| ~~`registry_logs`~~ | (삭제됨) | 김시연 | 예전 매물 시세 변동 이력. 2026-10-05 코드 삭제 |
| `notifications` | `domain/notification/entity/Notification.java` | 김시연 | 사용자에게 발송된 알림 |
| `posts` | `domain/board/entity/Post.java` | 미정 | 커뮤니티 게시글 (카테고리 4종) |
| `post_metas` | `domain/board/entity/PostMeta.java` | 미정 | 게시글 부가정보 키/값 (매물 요약, 인테리어 태그·사진 등) |
| `comments` | `domain/board/entity/Comment.java` | 미정 | 댓글/답변/대댓글 (+ 답변 채택) |
| `vote_options` | `domain/board/entity/VoteOption.java` | 미정 | 집 구하기 게시글의 투표 항목 |
| `vote_records` | `domain/board/entity/VoteRecord.java` | 미정 | 투표 내역 (1인 1표) |
| `post_likes` | `domain/board/entity/PostLike.java` | 미정 | 게시글 좋아요 |
| `post_scraps` | `domain/board/entity/PostScrap.java` | 미정 | 게시글 스크랩 |
| `listing_reports` | `domain/listing/entity/ListingReport.java` | 송귀성 | 추천 매물(더미 매물 CSV) 허위매물 신고 (회원당 매물 1번) |
| `listing_favorites` | `domain/listing/entity/ListingFavorite.java` | 송귀성 | 추천 매물 관심매물 (마이페이지 관심 매물에 함께 표시) |
| `app_settings` | `domain/appsetting/entity/AppSetting.java` | 송귀성 | 관리자 수정 > 기타 설정 (싱글톤 1행, id=1). `recommendation_limit INT` = 추천 개수 상한(월세·전세 각각, 기본 500, 10~1000). 서버 최초 기동 때 시더가 기본값으로 만들고, 진단 요청마다 AI 엔진에 실어 보낸다. 운영(validate)에는 `CREATE TABLE app_settings (id BIGINT PRIMARY KEY, recommendation_limit INT, created_at DATETIME(6), updated_at DATETIME(6));` 필요 |
| `loan_products` | `domain/loan/entity/LoanProduct.java` | 송귀성 | 전세자금대출 5종의 자격 조건/우대사항 (관리자 수정 > 전세자금대출) |
| `loan_reference_links` | `domain/loan/entity/LoanReferenceLink.java` | 송귀성 | 대출별 참고 확인 페이지 주소 (자격 조건과 무관, 관리자 수정 > 전세자금대출) |
| ~~`registry_analysis`~~ | `domain/watchlist/entity/RegistryAnalysis.java` | 김시연 | ⏳ **미구현.** 실제 등기부등본 API 연동이 필요해 스키마 설계만 되어있는 상태 (자세한 내용은 9번 항목 참고) |

## 3. 테이블 상세

모든 테이블은 공통으로 `created_at`, `updated_at`(자동 채워짐, `BaseTimeEntity` 상속)을 가집니다.
아래 표에는 그 둘을 생략했습니다.

### users (회원)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| email | VARCHAR(191) | UNIQUE, NOT NULL | 로그인 아이디 |
| password | VARCHAR(100) | NULL 허용 | BCrypt 암호화. 네이버 전용 계정은 null |
| nickname | VARCHAR(50) | NOT NULL | |
| phone | VARCHAR(20) | NULL 허용 | 휴대폰 번호(010-0000-0000). 마이페이지에서 선택 입력 — 운영(`validate`)은 `ALTER TABLE users ADD COLUMN phone VARCHAR(20);` 수동 적용 필요 |
| provider | VARCHAR(20) | NOT NULL | `LOCAL` \| `NAVER` |
| provider_id | VARCHAR(100) | | 소셜 로그인 고유 ID |
| refresh_token | VARCHAR(500) | | 최근 발급된 JWT refresh token (대조용) |
| role | VARCHAR(20) | NOT NULL, 기본 `USER` | 권한 `USER` \| `ADMIN`. `ADMIN`은 상단 "관리자 수정" 탭과 `/api/admin/**` 사용 가능. 회원가입으로는 만들 수 없고 서버 시작 시 `AdminAccountInitializer`가 `.env`의 `ADMIN_PASSWORD`로 admin 계정을 만들거나 승격한다 — 운영(`validate`)은 `ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';` 수동 적용 필요 |

### housing_conditions (마이페이지 조건)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | **UNIQUE**, NOT NULL | 사용자 1명당 1건만 저장 (FK: users.id, 애플리케이션에서만 검증) |
| age | INT | | 나이(만) |
| annual_income | INT | NOT NULL | 소득(연소득, 만원) — 2026-09-16 이전엔 월급(월 단위)이었다가 연소득으로 통합 |
| couple_annual_income | INT | | 부부합산 연소득(만원, 선택) — 계산 시 annual_income과 비교해 더 큰 값을 씀 |
| work_location | VARCHAR | | 직장 위치 (자치구 이름, 예: "강남구") |
| work_address | VARCHAR(255) | | 카카오 주소검색으로 찾은 직장의 실제 도로명/지번 주소 (선택) — 2026-09-22 추가, 운영(`validate`)은 `ALTER TABLE housing_conditions ADD COLUMN work_address VARCHAR(255);` 수동 적용 필요 |
| work_lat / work_lon | DOUBLE | | 위 주소의 정확한 위도/경도 (선택, 함께 저장됨) — AI 진단 시 work_location 대표 좌표 대신 이 좌표로 통근시간 계산. 운영은 `ALTER TABLE housing_conditions ADD COLUMN work_lat DOUBLE, ADD COLUMN work_lon DOUBLE;` 수동 적용 필요 |
| desired_deposit | INT | | 희망 보증금(만원) |
| desired_rent | INT | | 희망 월세(만원) |
| real_estate_asset / car_asset / financial_asset / other_asset | INT | | 자산 구성(부동산/자동차/금융자산/일반자산, 만원) |
| financial_debt / other_debt | INT | | 부채 구성(금융부채/일반부채, 만원) — 총자산액(순자산) = 자산 합 - 부채 합, 저장 안 하고 매번 계산 |
| job_type | VARCHAR | | 직업종류 (GOVERNMENT/SME/MID_SIZED/LARGE_CORP) |
| no_householder | BOOLEAN | | 무주택여부 |
| military_service_months | INT | | 병역이행기간(개월, 선택) — 2026-10-01 추가. 아직 어느 대출/정책 판별에도 쓰지 않는 값(필드만 수집), 추후 특정 대출상품에 연동 예정 |
| notification_enabled | BOOLEAN | NOT NULL | ⭐ WatchList 알림 발송 여부를 여기서 최종 판단함 (9-2 참고) |

우대사항(preferentialStatuses, 다중 선택)은 별도 테이블 `housing_condition_preferences`
(id PK, `housing_condition_id` FK, `preferential_status` UNIQUE(housing_condition_id, preferential_status))에
사용자당 0~N건으로 저장된다. 2026-09-22 이전엔 JPA `@ElementCollection`(자체 기본키 없는 값 컬렉션)이었는데,
Aiven 등 관리형 클라우드 MySQL은 `sql_require_primary_key`가 켜져 있어 기본키 없는 테이블 생성 자체가
거부돼(`Unable to create or change a table without a primary key`) 실전 연동 중 발견했다. board 도메인의
PostMeta처럼 자체 id를 가진 진짜 엔티티(`HousingConditionPreference`)로 바꿔서 어떤 MySQL 설정에서도
동작하게 했다 (H2/로컬 MySQL은 원래도 문제없었음).

`preferential_status` 컬럼의 실제 타입 정정(2026-10-01): 엔티티 코드는 `@Enumerated(EnumType.STRING) @Column(length=30)`로
VARCHAR를 의도했지만, 실제 Aiven 운영 DB를 열어보니 Hibernate가 MySQL 네이티브 `ENUM(...)` 컬럼으로 생성해뒀다 - 이 문서가
그동안 VARCHAR로 잘못 적어둔 부분이다. 그래서 `PreferentialStatus`(마이페이지)/`LoanPreferenceKey`(전세자금대출) enum에
새 값을 추가할 때마다 이 컬럼의 ENUM 정의도 함께 넓혀야 한다 - `ddl-auto=update`(dev/local-mysql)는 자동으로
`ALTER TABLE ... MODIFY COLUMN ... ENUM(...)`을 실행해 넓혀주지만(기존 값은 유지, 순수 추가라 안전), `ddl-auto=validate`를
쓰는 실제 배포(application-prod.yml)는 그 DB에 이미 이 ENUM 정의가 반영돼 있어야 기동에 성공한다.
2026-10-01에 DUAL_INCOME/ONE_CHILD/TWO_CHILDREN/DISABLED/MULTICULTURAL/ELDERLY_DEPENDENT/ELDERLY_HOUSEHOLD 7종을
추가하면서, 로컬에서 `local-mysql` 프로필로 이 Aiven DB에 접속해 이미 위 ENUM 확장과 `military_service_months` 컬럼
추가를 적용해뒀다 - 배포가 이 Aiven 인스턴스를 그대로 쓴다면 별도 수동 반영 없이도 `validate`를 통과한다.

### payments (단건 결제)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | FK: users.id |
| payment_key | VARCHAR(200) | UNIQUE | 토스페이먼츠가 발급하는 결제 고유키 |
| order_id | VARCHAR(64) | **UNIQUE**, NOT NULL | 우리 쪽에서 만든 주문번호 |
| order_name | VARCHAR(100) | | 예: "맞집 안심 매물 리포트" |
| amount | BIGINT | NOT NULL | 결제 금액(원) |
| status | VARCHAR(20) | NOT NULL | `PENDING` → `PAID` (또는 `FAILED`/`CANCELED`) |

### subscriptions (정기 구독)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | **UNIQUE**, NOT NULL | 사용자 1명당 구독 1건 |
| billing_key | VARCHAR(200) | | 토스페이먼츠 자동결제 수단 키 |
| customer_key | VARCHAR(100) | UNIQUE, NOT NULL | 토스페이먼츠 쪽 고객 식별자 |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` \| `CANCELED` |
| next_payment_date | DATE | | 다음 자동결제 예정일 (스케줄러가 매일 09시 이 값을 확인) |
| expired_at | DATE | | (예약 필드, 아직 미사용) |

### properties (매물)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| address | VARCHAR(300) | **UNIQUE**, NOT NULL | 같은 주소는 매물 1건으로 합쳐짐 → 여러 사용자가 같은 매물을 관심 등록 가능 |
| region | VARCHAR(50) | | 예: "관악구" |
| deposit | INT | NOT NULL | 보증금(만원) — 시세 변동 감지 시 이 값이 갱신됨 |
| monthly_rent | INT | NOT NULL | 월세(만원) |
| maintenance_fee | INT | | 관리비(만원) |
| source_url | VARCHAR(500) | | 매물 원본 출처 |
| report_count | INT | NOT NULL | 허위 매물 신고 누적 횟수 |

### favorites (관심 매물 = WatchList)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | FK: users.id |
| property_id | BIGINT | NOT NULL | FK: properties.id |
| status | VARCHAR(20) | NOT NULL | `WATCHING`(모니터링 중) → `CHANGED`(변동 감지됨) / `REMOVED`(삭제, 소프트 삭제) |

> `(user_id, property_id)`는 사실상 유니크해야 하지만 지금은 DB 제약이 아니라 서비스 코드에서
> "이미 있으면 재사용"으로 처리하고 있습니다. 동시성이 걱정되면 유니크 인덱스를 추가하세요 (9-1 참고).

### registry_logs (매물 변동 이력)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| property_id | BIGINT | NOT NULL | FK: properties.id |
| change_type | VARCHAR(50) | | 예: "보증금 변경", "월세 변경" |
| description | VARCHAR(500) | | 예: "보증금 1000 → 1200만원" |
| detected_at | DATETIME | | 감지 시각 |

### notifications (알림)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL | FK: users.id |
| title | VARCHAR(100) | NOT NULL | |
| content | VARCHAR(500) | NOT NULL | |
| is_read | BOOLEAN | NOT NULL | 읽음 여부 (JPA 필드명은 `read`지만, MySQL 예약어라 컬럼명만 `is_read`로 매핑) |

### 커뮤니티 게시판 (posts 외 6개, 담당: 미정)

카테고리(`HOUSING` 집 구하기 / `INTERIOR` 인테리어 / `SAFETY` 전세사기·법률 / `COMMUNITY` 자취 꿀팁)는
테이블이 아니라 `BoardCategory` enum이고 `posts.category`에 문자열로 저장됩니다.
`post_id`/`option_id`/`user_id`/`writer_id`는 다른 도메인처럼 FK 제약 없이 id만 들고 있습니다
(자식 행 삭제는 `PostService.delete`가 처리: metas/options/comments는 JPA cascade, 투표·좋아요·스크랩은 직접 삭제).

#### posts
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| category | VARCHAR(20) | NOT NULL | `HOUSING` \| `INTERIOR` \| `SAFETY` \| `COMMUNITY`. 인덱스 `idx_posts_category_created (category, created_at)` |
| title | VARCHAR(100) | NOT NULL | |
| content | TEXT | NOT NULL | 최대 10,000자(서비스에서 검증) |
| writer_id | BIGINT | NOT NULL | 익명 글도 저장하지만 API 응답에는 절대 내려가지 않음 |
| is_anonymous | BOOLEAN | NOT NULL | SAFETY에서만 true 허용 (`anonymous`가 예약어라 컬럼명만 `is_anonymous`) |
| view_count / like_count / comment_count | BIGINT | NOT NULL | 원자 UPDATE(`n = n + 1`)로만 갱신 |
| solved | BOOLEAN | NOT NULL | SAFETY 답변 채택 완료 여부 |

#### post_metas (부가정보 키/값)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| post_id | BIGINT | NOT NULL | FK: posts.id |
| meta_key | VARCHAR(40) | NOT NULL | `PostMetaKey` enum. HOUSING: LISTING_ADDRESS/DEPOSIT/MONTHLY_RENT/AREA/TYPE/URL, INTERIOR: HOUSING_TYPE/AREA_PYEONG/IMAGE_URL(여러 개)/IMAGE_PIN(여러 개, JSON 문자열) |
| meta_value | TEXT | NOT NULL | 키별 최대 길이는 `PostMetaKey.maxLength` |
| sort_order | INT | NOT NULL | 같은 키가 여러 개일 때(사진 슬라이드) 순서 |

새 부가정보가 필요하면 `PostMetaKey`에 키만 추가하면 됩니다 (테이블 변경 없음).

#### comments
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| post_id | BIGINT | NOT NULL | FK: posts.id |
| writer_id | BIGINT | NOT NULL | |
| parent_id | BIGINT | NULL 허용 | null이면 댓글/답변, 값이 있으면 대댓글(1단계까지) |
| content | VARCHAR(2000) | NOT NULL | |
| is_selected | BOOLEAN | NOT NULL | 채택된 답변 (SAFETY 최상위 댓글만, 글당 1개) |
| selected_at | DATETIME | | 채택 시각 |

#### vote_options / vote_records / post_likes / post_scraps
| 테이블 | 컬럼 | 제약 |
|---|---|---|
| vote_options | id PK, post_id, label VARCHAR(50), sort_order INT, vote_count BIGINT | 글당 2~5개(서비스 검증) |
| vote_records | id PK, post_id, option_id, user_id | **UNIQUE(post_id, user_id)** `uk_vote_post_user` = 1인 1표 |
| post_likes | id PK, post_id, user_id | **UNIQUE(post_id, user_id)** `uk_like_post_user` |
| post_scraps | id PK, post_id, user_id | **UNIQUE(post_id, user_id)** `uk_scrap_post_user` |

#### listing_reports / listing_favorites (추천 매물 신고·관심)
추천 매물은 DB가 아니라 `docs/samples/dummyhouses/*.csv`에서 오므로 매물 테이블이 없습니다. 두 테이블 모두 CSV의 `매물등록번호`
(예: `SEOCHO-202609-0001`)를 `listing_id` 문자열로 저장하고, 회원은 FK 없이 `user_id`만 저장합니다.
기존 `properties`/`favorites`와는 별개입니다 (`properties.address`가 UNIQUE라 한 주소를 공유하는 더미 매물을 담을 수 없어서).

| 테이블 | 컬럼 | 제약 |
|---|---|---|
| listing_reports | id PK, listing_id VARCHAR(40), user_id BIGINT, report_type VARCHAR(30), message VARCHAR(1500) | **UNIQUE(listing_id, user_id)** `uk_listing_report_user_listing` = 회원당 매물 1번 신고, 인덱스 `idx_listing_report_listing`. 신고 수가 2건 이상이면 카드에 "허위매물 주의"(서버 `ListingReportService.FLAG_THRESHOLD`) |
| listing_favorites | id PK, user_id BIGINT, listing_id VARCHAR(40), address VARCHAR(300), region VARCHAR(30), lease_type VARCHAR(10), building_name VARCHAR(100), property_type VARCHAR(30), unit_label VARCHAR(60), deposit INT, monthly_rent INT, maintenance_fee INT, snapshot TEXT, **previous_deposit INT, previous_monthly_rent INT, price_changed_at DATETIME(6)** | **UNIQUE(user_id, listing_id)** `uk_listing_fav_user_listing`, 인덱스 `idx_listing_fav_user`. 주소/가격 컬럼은 예전 관심매물의 간단 표시용이고, `snapshot`(JSON)에 리포트 카드의 전체 매물 정보를 통째로 저장해 마이페이지에서 리포트와 같은 카드로 다시 그린다. 운영(validate)에는 `ALTER TABLE listing_favorites ADD COLUMN snapshot TEXT;` 필요. **2026-10-05** 가격 변동 알림용으로 `previous_deposit`/`previous_monthly_rent`/`price_changed_at`가 추가됐다(매물 수정으로 보증금·월세가 바뀌면 옛 가격을 남기고 새 가격으로 갱신, `snapshot`은 비운다). 운영(validate)에는 `ALTER TABLE listing_favorites ADD COLUMN previous_deposit INT, ADD COLUMN previous_monthly_rent INT, ADD COLUMN price_changed_at DATETIME(6);` 필요 |

> 회원 탈퇴 시 `listing_favorites`는 함께 지우고, `listing_reports`는 신고 누적 집계를 위해 남깁니다.

#### loan_products (전세자금대출 조건, 관리자 수정)
대출 4종(`GENERAL_BEOTIMMOK`, `YOUTH_BEOTIMMOK`, `NEWBORN_BEOTIMMOK`, `YOUTH_MONTHLY_RENT`)은 코드(`LoanType`)에 고정이고, 저장된 조건만 행으로 있다(삭제하면 행이 지워져 "미설정"). 중소기업 청년 버팀목 전세대출(`SME_YOUTH_BEOTIMMOK`)은 상품이 없어져 코드에서 제거됨 - 저장된 행이 있었어도 더는 조회되지 않는다.
값이 NULL이면 그 조건은 "제한 없음"이다. 이후 이자 계산식이 이 조건으로 자격 판별과 우대금리 차감을 한다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| loan_type | VARCHAR(30) | NOT NULL, **UNIQUE** `uk_loan_product_type` | `LoanType` 코드 |
| min_age / max_age | INT | | 나이(만) 최소/최대 |
| max_income_single / max_income_couple | INT | | 연소득 이하(개인/부부합산, 만원) |
| max_asset | INT | | 총자산 이하(만원) |
| max_listing_deposit | INT | | 매물 보증금 이하(만원) — 넘는 매물에는 이 대출을 적용하지 않음 |
| max_exclusive_area | DOUBLE | | 전용면적 이하(㎡) |
| max_loan_ratio_percent | DOUBLE | | 최대 대출금 비율한도(%) — 매물 보증금 중 이 비율까지만 대출 가능 (2026-09-29) |
| max_loan_amount | INT | | 최대 대출금액(만원) — 매물과 무관한 대출 절대 상한. 비율한도와 절대 상한을 둘 다 넣으면 더 낮은 쪽이 실제 한도 (2026-09-29) |
| preferences | TEXT | | 우대사항(기초생활수급자·차상위계층·한부모가구·자립준비청년·신혼부부(기혼자포함)·맞벌이부부·1자녀·2자녀·다자녀가구·장애인·다문화가구·노인부양가구·고령자가구·무주택여부·중소기업 취업청년, LoanPreferenceKey 참고) JSON: `{"NEWLYWED": {"required": false, "discount": 0.2}, ...}` (필수 여부 + 우대금리 차감 %p) |

#### loan_reference_links (전세자금대출 참고 확인 페이지 주소, 관리자 수정)
대출별 "참고 확인 페이지" 주소만 보관한다(2026-09-29). 자격 조건·계산 로직과는 전혀 무관하고, 관리자가 그 대출을 조사할 때
참고한 홈페이지 링크를 적어두는 용도다. `loan_products`와 일부러 같은 테이블에 두지 않았다 — 그래야 이 링크만 저장해도
그 대출이 "저장됨"(모든 매물에 적용)으로 바뀌지 않는다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| loan_type | VARCHAR(30) | NOT NULL, **UNIQUE** `uk_loan_reference_link_type` | `LoanType` 코드 |
| reference_url | VARCHAR(500) | | 참고 확인 페이지 주소. 비어 있으면 저장 안 함 |

> ⚠️ 운영(`ddl-auto: validate`)에서는 위 11개 테이블을 수동으로 만들어야 합니다.
> 가장 간단한 방법: dev(H2/로컬 MySQL)를 `update`로 띄워 테이블을 자동 생성시킨 뒤 `SHOW CREATE TABLE posts;` 등으로 DDL을 뽑아 운영에 적용하세요
> (컬럼 규칙: 카멜케이스 필드 → 스네이크케이스, 예: `viewCount` → `view_count`).

## 4. 관계 요약

```
users 1 ─── 0..1 housing_conditions   (사용자당 조건 1건)
users 1 ─── N   payments              (결제 이력 여러 건)
users 1 ─── 0..1 subscriptions        (사용자당 구독 1건)
users 1 ─── N   favorites             (관심 매물 여러 건)
users 1 ─── N   notifications         (알림 여러 건)

properties 1 ─── N favorites          (매물 하나를 여러 사용자가 관심 등록)
properties 1 ─── N registry_logs      (매물 하나의 변동 이력 여러 건)

users 1 ─── N   posts / comments      (writer_id, FK 없음)
posts 1 ─── N   post_metas / comments / vote_options
vote_options 1 ─── N vote_records     (post_id+user_id 유니크 = 1인 1표)
users N ─── N   posts                 (post_likes, post_scraps 로 연결)
users 1 ─── N   listing_reports / listing_favorites  (user_id, FK 없음. 매물은 CSV의 매물등록번호 문자열)
loan_products / loan_reference_links   (대출 종류 코드 5종 고정, 다른 테이블과 연결 없음)
```

전부 애플리케이션 레벨 FK입니다 (JPA `@ManyToOne` 대신 `Long userId`/`propertyId`로 직접 들고 있는
"가볍고 직관적인" 설계 — 기획서의 WatchList 설계 방향과 동일). DB 차원의 FK 제약(`REFERENCES`)은
걸려있지 않으니, 정합성이 걱정되면 9-1을 참고해 추가하세요.

## 5. 외래키(FK)를 DB 레벨 @ManyToOne으로 안 한 이유

JPA `@ManyToOne User user`처럼 실제 연관관계로 걸면 자동으로 FK 제약과 조인이 생기지만,
지연로딩/N+1 문제, 순환참조 위험이 같이 따라옵니다. 지금 단계(MVP)는 각 도메인이 독립적으로
`Long userId`만 들고 있는 편이 이해하기 쉽고 도메인 간 결합도도 낮아서 이 방식을 택했습니다.
데이터가 많아지고 조인 쿼리가 필요해지면 그때 `@ManyToOne`으로 리팩터링을 고려하세요.

## 6. 테이블을 고치고 싶을 때 (김시연 워크플로우)

1. `domain/{도메인}/entity/*.java` 파일에서 필드를 추가/수정합니다. (예: `favorites`에 컬럼 추가 → `WatchlistItem.java` 수정)
2. `ddl-auto: update` 덕분에 서버를 재시작하기만 하면 H2/MySQL 테이블에 컬럼이 자동으로 추가됩니다.
   - ⚠️ 컬럼 **삭제**나 **타입 변경**은 `update`가 알아서 못 해줍니다 (안전을 위해 일부러 그렇게 만들어짐).
     이런 경우 로컬 MySQL이면 테이블을 직접 `ALTER TABLE`/`DROP` 하거나, H2는 그냥 서버를 재시작하면
     테이블이 통째로 새로 만들어집니다.
3. 관련 DTO(`dto/*.java`)와 Repository 조회 메서드도 필요하면 같이 고칩니다.
4. 고친 뒤에는 이 문서(2~4번 항목)와 `DB구조도.html`도 함께 업데이트해주세요.

## 7. 실제 데이터 확인하는 법

- **H2 사용 중(기본값)**: 서버를 띄운 상태에서 브라우저로 http://localhost:8080/h2-console 접속
  → JDBC URL에 `jdbc:h2:mem:customhouse` 입력, 사용자명 `sa`, 비밀번호는 빈 칸으로 접속.
- **로컬 MySQL 사용 중**: MySQL Workbench 등으로 `customhouse` 데이터베이스에 직접 접속.

## 8. 트러블슈팅 (실제로 겪었던 문제)

### "JWT/네이버 로그인 관련 설정이 없는데 서버가 안 켜져요"
`backend/.env`에 `JWT_SECRET=`처럼 **키는 있는데 값이 빈 줄**을 남겨두면 안 됩니다.
Spring은 "프로퍼티가 존재하는지"만 보고 `${JWT_SECRET:기본값}`의 기본값 적용 여부를 정하기 때문에,
빈 값이어도 "존재"로 인식해서 기본값 대신 빈 문자열을 그대로 씁니다 (JWT는 "0 bits" 에러로 기동 실패,
네이버 OAuth2는 "Client id must not be empty"로 기동 실패). **안 쓸 값은 줄 자체를 `#`으로 주석 처리**하세요
(`.env.example`이 이미 이렇게 되어 있습니다).

### "H2에서는 됐는데 MySQL로 바꾸니까 테이블이 하나 안 생겨요"
`notifications` 테이블의 `read` 컬럼이 **MySQL 예약어**라 `create table` 자체가 실패했던 적이 있습니다
(H2는 `MODE=MySQL`이어도 이 경우를 안 걸러냄). 컬럼명을 지을 때 `read`, `key`, `order`, `group`, `table`,
`condition`, `row`, `level` 같은 흔한 단어는 피하거나, `@Column(name = "실제_컬럼명")`으로 안전한 이름을
따로 지정하세요 (Notification.java의 `is_read`가 예시입니다). H2로만 개발하면 이런 문제가 조용히
숨어있다가 실제 MySQL로 옮길 때 터지니, 새 테이블을 추가했다면 한 번쯤 `local-mysql` 프로필로도
띄워보는 걸 추천합니다.

## 9. 다음에 할 일 (TODO)

### 9-1. DB 레벨 제약 강화
- `favorites(user_id, property_id)` 복합 유니크 인덱스 추가 검토
- 필요하면 실제 FK 제약(`ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY`)으로 승격

### 9-2. 알림 발송 대상 판단 로직
지금은 `WatchlistService`가 매물 변동을 감지하면, 그 매물을 관심 등록한 사용자들 중
`housing_conditions.notification_enabled = true`인 사람에게만 알림을 보냅니다
(즉 마이페이지에서 알림을 꺼둔 사람, 혹은 마이페이지 자체를 설정 안 한 사람은 알림을 못 받음).
알림을 더 세분화(예: "가격 변동만", "허위매물 신고만")하고 싶으면 `housing_conditions`에
`notification_enabled` 대신 여러 개의 boolean 컬럼을 추가하는 방식으로 확장하면 됩니다.

### 9-3. registry_analysis (미구현)
기획서상 "등기부등본 위험도 분석" 테이블입니다. 실제 등기부등본 열람 API(유료, 본인인증 필요)
연동이 선행되어야 해서 이번 범위에서는 제외했습니다. `domain/watchlist/entity/RegistryAnalysis.java`에
필드 설계(propertyId, riskScore, summary)만 POJO로 남겨뒀습니다 — 실제로 구현할 때 이 파일을
`@Entity`로 전환하고 `registry_logs`처럼 Repository/Service/Controller를 붙이면 됩니다.

### 9-4. 실제 매물 데이터 연동
지금 `properties`는 사용자가 프론트엔드 폼으로 직접 입력해서 채워집니다 (실거래가 API 미연동).
국토교통부 전월세 실거래가 API 등이 붙으면, 그 배치가 `properties`를 채우고
`RegistryLog`를 자동으로 쌓는 형태로 자연스럽게 확장할 수 있습니다
(`customhouse-ai/app/services/api_collector.py`의 TODO와 같은 맥락).

#### 관심매물 알림 (2026-10-05, `domain/listing/service/ListingAlertService.java`)

- 하트로 담은 관심매물(`listing_favorites`)의 **보증금/월세가 바뀌면**(등록자가 매물을 수정할 때) 그 매물을 담은 모든 회원의 알림함(`notifications`)에 "관심 매물 가격 변동" 알림이 쌓인다.
- 매물의 **허위매물 신고가 2건에 처음 도달하면**(`ListingReportService.FLAG_THRESHOLD`) 담은 모든 회원의 알림함에 "관심 매물 허위매물 경고" 알림이 쌓인다.
- 위 두 경우 **메일**은 마이페이지 "알림 수신"(`housing_conditions.notification_enabled`)을 켠 회원에게만 간다(SendGrid, 실패해도 알림함 알림은 유지).
- 알림은 `DELETE /api/notifications/{id}`로 본인 것만 삭제한다.
