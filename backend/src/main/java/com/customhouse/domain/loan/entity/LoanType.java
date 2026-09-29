package com.customhouse.domain.loan.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * [담당: 송귀성] 전세자금대출 5종 (관리자 수정 > 전세자금대출 하위탭). 종류는 고정이고 조건만 관리자 화면에서 입력한다.
 * 화면/계산식이 이 코드(name)를 키로 쓰므로 이름을 바꾸지 말 것.
 */
public enum LoanType {
    GENERAL_BEOTIMMOK("일반 버팀목 전세대출", "전세"),
    YOUTH_BEOTIMMOK("청년전용 버팀목 전세대출", "전세"),
    SME_YOUTH_BEOTIMMOK("중소기업 청년 버팀목 전세대출", "전세"),
    NEWBORN_BEOTIMMOK("신생아 특례 버팀목대출", "전세"),
    YOUTH_MONTHLY_RENT("청년전용 보증부월세대출", "월세");

    private final String label;
    /** 이 대출이 붙는 매물 유형(전세/월세). 리포트에서 전세 매물엔 전세 대출을, 월세 매물엔 월세 대출을 보여준다. */
    private final String leaseType;

    LoanType(String label, String leaseType) {
        this.label = label;
        this.leaseType = leaseType;
    }

    public String getLabel() {
        return label;
    }

    public String getLeaseType() {
        return leaseType;
    }

    public static Optional<LoanType> fromCode(String code) {
        return Arrays.stream(values()).filter(t -> t.name().equals(code)).findFirst();
    }
}
