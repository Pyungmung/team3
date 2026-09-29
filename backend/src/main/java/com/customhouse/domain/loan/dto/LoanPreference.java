package com.customhouse.domain.loan.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * [담당: 송귀성] 대출 우대사항 1개 설정. required=true면 이 우대사항이 대출 자격의 필수조건이고,
 * discount는 이 우대사항에 해당할 때 깎아주는 우대금리(%p, 연 이자율에서 뺀다)다.
 * 2026-09-30: 신혼부부/다자녀가구처럼 일부 우대사항은 공통 조건과 다른(보통 더 관대한) 자체 한도를 쓴다 -
 * override* 5종이 그 값이다. null이면 공통 조건(매물 보증금 제한/연소득/최대 대출금액/최대 대출금 비율한도)을
 * 그대로 쓰고, 값이 있고 사용자가 그 우대사항에 해당하면 그 값으로 대체된다(loan_matcher._effective_limit 참고).
 */
public record LoanPreference(
        boolean required,
        @DecimalMin(value = "0", message = "우대금리 차감은 0 이상이어야 합니다.")
        @DecimalMax(value = "10", message = "우대금리 차감은 10%p 이하로 입력해주세요.") Double discount,
        @Min(value = 0, message = "우대 매물 보증금 한도는 0 이상이어야 합니다.")
        @Max(value = 100_000_000, message = "우대 매물 보증금 한도가 너무 큽니다.") Integer overrideMaxListingDeposit,
        @Min(value = 0, message = "우대 연소득(개인) 한도는 0 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "우대 연소득(개인) 한도가 너무 큽니다.") Integer overrideMaxIncomeSingle,
        @Min(value = 0, message = "우대 연소득(부부합산) 한도는 0 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "우대 연소득(부부합산) 한도가 너무 큽니다.") Integer overrideMaxIncomeCouple,
        @Min(value = 0, message = "우대 최대 대출금액은 0 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "우대 최대 대출금액이 너무 큽니다.") Integer overrideMaxLoanAmount,
        @DecimalMin(value = "0", message = "우대 최대 대출금 비율한도는 0 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "우대 최대 대출금 비율한도는 100% 이하로 입력해주세요.") Double overrideMaxLoanRatioPercent
) {
    public static final LoanPreference EMPTY = new LoanPreference(false, 0.0, null, null, null, null, null);
}
