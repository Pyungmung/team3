package com.customhouse.domain.loan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * [담당: 송귀성] 대출 우대사항 1개 설정. required=true면 이 우대사항이 대출 자격의 필수조건이고,
 * discount는 이 우대사항에 해당할 때 깎아주는 우대금리(%p, 연 이자율에서 뺀다)다.
 */
public record LoanPreference(
        boolean required,
        @DecimalMin(value = "0", message = "우대금리 차감은 0 이상이어야 합니다.")
        @DecimalMax(value = "10", message = "우대금리 차감은 10%p 이하로 입력해주세요.") Double discount
) {
    public static final LoanPreference EMPTY = new LoanPreference(false, 0.0);
}
