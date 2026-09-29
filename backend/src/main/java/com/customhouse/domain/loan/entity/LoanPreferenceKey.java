package com.customhouse.domain.loan.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * [담당: 송귀성] 정부 주거지원 정책 우대사항. 대출마다 "필수조건 여부"와 "우대금리 차감(%p)"을 입력한다.
 * 이후 이자 계산식이 사용자가 해당하는 우대사항의 차감분을 자동으로 뺀다. 선언 순서가 화면 표시 순서다.
 */
public enum LoanPreferenceKey {
    BASIC_LIVELIHOOD("기초생활수급자"),
    NEAR_POOR("차상위계층"),
    SINGLE_PARENT("한부모가족"),
    INDEPENDENT_YOUTH("자립준비청년"),
    NEWLYWED("신혼부부"),
    MULTI_CHILD("다자녀가구"),
    NO_HOME("무주택여부"),
    /** 주거조건 입력의 직업 유형(jobType=SME, 중소기업 재직)과 연결되는 항목이다. */
    SME_EMPLOYED_YOUTH("중소기업 취업청년");

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
