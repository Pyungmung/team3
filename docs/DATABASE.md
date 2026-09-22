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

1. `backend/.env` 파일을 열어서 (없으면 `backend/.env.example`을 복사) `DB_USERNAME`, `DB_PASSWORD`를
   본인 MySQL 계정 정보로 채웁니다. **비밀번호는 여기(.env)에만 적고, 깃/채팅에는 절대 올리지 마세요.**
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
| `properties` | `domain/watchlist/entity/Property.java` | 김시연 | 매물 정보 (주소 기준으로 여러 사용자가 공유) |
| `favorites` | `domain/watchlist/entity/WatchlistItem.java` | 김시연 | 관심 매물 (사용자 ↔ 매물 연결) |
| `registry_logs` | `domain/watchlist/entity/RegistryLog.java` | 김시연 | 매물 시세/상태 변동 이력 |
| `notifications` | `domain/notification/entity/Notification.java` | 김시연 | 사용자에게 발송된 알림 |
| `posts` | `domain/board/entity/Post.java` | 미정 | 커뮤니티 게시글 (카테고리 4종) |
| `post_metas` | `domain/board/entity/PostMeta.java` | 미정 | 게시글 부가정보 키/값 (매물 요약, 인테리어 태그·사진 등) |
| `comments` | `domain/board/entity/Comment.java` | 미정 | 댓글/답변/대댓글 (+ 답변 채택) |
| `vote_options` | `domain/board/entity/VoteOption.java` | 미정 | 집 구하기 게시글의 투표 항목 |
| `vote_records` | `domain/board/entity/VoteRecord.java` | 미정 | 투표 내역 (1인 1표) |
| `post_likes` | `domain/board/entity/PostLike.java` | 미정 | 게시글 좋아요 |
| `post_scraps` | `domain/board/entity/PostScrap.java` | 미정 | 게시글 스크랩 |
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
| notification_enabled | BOOLEAN | NOT NULL | ⭐ WatchList 알림 발송 여부를 여기서 최종 판단함 (9-2 참고) |

우대사항(preferentialStatuses, 다중 선택)은 별도 테이블 `housing_condition_preferences`
(`housing_condition_id` FK + `preferential_status` VARCHAR)에 사용자당 0~N건으로 저장된다
(JPA `@ElementCollection`).

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

> ⚠️ 운영(`ddl-auto: validate`)에서는 위 7개 테이블을 수동으로 만들어야 합니다.
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
