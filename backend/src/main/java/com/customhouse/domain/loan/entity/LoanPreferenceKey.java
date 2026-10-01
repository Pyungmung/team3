package com.customhouse.domain.loan.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * [담당: 송귀성] 정부 주거지원 정책 우대사항. 대출마다 "필수조건 여부"와 "우대금리 차감(%p)"을 입력한다.
 * 이후 이자 계산식이 사용자가 해당하는 우대사항의 차감분을 자동으로 뺀다. 선언 순서가 화면 표시 순서다.
 * DUAL_INCOME/ONE_CHILD/TWO_CHILDREN/MULTI_CHILD는 관리자 화면(admin/loans.html)에서 "부가 우대사항"으로
 * 시각적으로만 묶여 보인다 - 묶음일 뿐 로직상 각각 완전히 독립된 우대사항이다(한 대출이 넷 다 체크해도 되고
 * 하나만 체크해도 된다). 선언 순서를 바꾸면 화면의 묶음 표시도 깨지니 이 네 개는 항상 붙여서 둔다.
 */
public enum LoanPreferenceKey {
    BASIC_LIVELIHOOD("기초생활수급자"),
    NEAR_POOR("차상위계층"),
    SINGLE_PARENT("한부모가구"),
    INDEPENDENT_YOUTH("자립준비청년"),
    NEWLYWED("신혼부부(기혼자포함)"),
    DUAL_INCOME("맞벌이부부"),
    ONE_CHILD("1자녀"),
    TWO_CHILDREN("2자녀"),
    MULTI_CHILD("다자녀가구"),
    DISABLED("장애인"),
    MULTICULTURAL("다문화가구"),
    ELDERLY_DEPENDENT("노인부양가구"),
    ELDERLY_HOUSEHOLD("고령자가구"),
    NO_HOME("무주택여부"),
    /** 주거조건 입력의 직업 유형(jobType=SME, 중소기업 재직)과 연결되는 항목이다. */
    SME_EMPLOYED_YOUTH("중소기업 취업청년"),
    /** 체크박스가 아니라 진단 폼의 "만 나이" 입력값(25세 미만/이상)으로 자동 판별된다 - 둘 다 청년전용
     * 버팀목 전세대출 "전용" 조건이라 다른 대출에는 의미가 없다(관리자 화면도 다른 대출 표에서는 아예 뺀다).
     * AGE_UNDER_25는 "(청년형) 만 25세 미만" 기준값(전용면적 60㎡ · 최대 대출금액 12000만원) 쪽 우대금리/
     * 재반영을 따로 줄 때 쓰고, AGE_25_OR_OLDER는 그보다 넓은 값으로 재반영한다(2026-10-01). */
    AGE_UNDER_25("만 25세 미만"),
    /** 재반영이 "더 관대한 값이 이긴다"는 기존 방향(_effective_limit)과 일치하도록 "미만"이 아니라
     * "이상"을 조건으로 잡았다 - 공통 조건(25세 미만 기준)보다 넓혀주는 쪽. */
    AGE_25_OR_OLDER("만 25세 이상");

    private final String label;

    LoanPreferenceKey(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<LoanPreferenceKey> fromCode(String code) {
        return Arrays.stream(values()).filter(k -> k.name().equals(code)).findFirst();
    }
}
