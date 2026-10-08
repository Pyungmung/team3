# 맞집 (청년 주거비 절약 웹앱)

**스택**: FE 3000(순수 HTML/JS+Tailwind CDN, `python frontend/serve.py`) · BE 8080(Spring Boot+JWT, dev=H2/prod=MySQL) · AI 8000(Python/FastAPI)

**규칙**: 작업 브랜치는 `claude-mvp`. `develop`/`main`엔 확인 없이 push 금지. 시크릿은 `.env`만 사용(Git/채팅 금지, 안 쓰는 키는 주석 처리). 새 파일 상단에 `[담당: 이름]` 주석. 복잡한 구현은 Plan 모드로 먼저 계획 후 빌드/테스트. Claude의 모든 답변은 한글로 작성.

**아키텍처**: 응답은 `ApiResponse<T>`+`CustomException`. JWT는 로그인 시 발급→localStorage→Authorization 헤더. 도메인 간 참조는 FK 없이 `Long id`(board/mypage 하위 테이블만 예외, cascade 사용).

**상세 문서**: DB는 `docs/DATABASE.md`+`docs/DB구조도.html`, 정책 데이터는 DB `housing_policies`(관리자수정>주거지원정책 탭에서 수정, CSV/policies.json 연동 끊김) 참고.

**전세자금대출 매칭/이자 로직**: 관리자수정>전세자금대출 탭의 소득/보증금/우대사항 조건을 loan_matcher.match_eligible_loans가 매물별로 판별. 금리는 대출별 저장된 금리표(소득x보증금 구간)가 있으면 그 값, 없으면 기준금리API(한국부동산원 전환율)+우대금리 차감(is_temporary_rate로 구분 표시). 대출원금=매물보증금-보유보증금(부족분, 전세만, 월세는 0). 한도는 비율한도/절대상한 중 낮은 값, 우대사항 재반영값 있으면 공통한도 대체. 이자=부족분x금리/12, 실질주거비=월세+관리비+이자.

**통근시간/실거래참고 로직**: 검색/매칭은 직선거리 추정(calculator._estimate_commute_minutes_fallback)만 쓰고 카카오API 호출 없음(2026-10-01~). 정확값은 화면에 보이는(스크롤 로딩된) 카드만 GET .../listings/{id}/commute로 온디맨드 조회(wireCommuteAutoLoad). 실거래참고(.../reference)도 같은 패턴. 단독다가구는 국토부API 지번없어 더미매물 원본거래값(ref_계약일/보증금/월세)으로 signature match 재발견, 신규매물은 동단위 참고 대체.
