package com.customhouse.domain.loan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * [담당: 송귀성] 전세자금대출 조건 저장 요청 (관리자 화면의 "수정완료"). 금액은 만원 단위, null은 "제한 없음".
 * preferences의 키는 LoanPreferenceKey에 있는 항목만 허용한다 (서비스에서 확인).
 */
public record LoanProductRequest(
        @Min(value = 0, message = "최소 나이는 0 이상이어야 합니다.") @Max(value = 120, message = "최소 나이가 너무 큽니다.") Integer minAge,
        @Min(value = 0, message = "최대 나이는 0 이상이어야 합니다.") @Max(value = 120, message = "최대 나이가 너무 큽니다.") Integer maxAge,
        @Min(value = 0, message = "연소득(개인)은 0 이상이어야 합니다.") @Max(value = 10_000_000, message = "연소득(개인)이 너무 큽니다.") Integer maxIncomeSingle,
        @Min(value = 0, message = "연소득(부부합산)은 0 이상이어야 합니다.") @Max(value = 10_000_000, message = "연소득(부부합산)이 너무 큽니다.") Integer maxIncomeCouple,
        @Min(value = 0, message = "총자산은 0 이상이어야 합니다.") @Max(value = 100_000_000, message = "총자산이 너무 큽니다.") Integer maxAsset,
        @Min(value = 0, message = "매물 보증금은 0 이상이어야 합니다.") @Max(value = 100_000_000, message = "매물 보증금이 너무 큽니다.") Integer maxListingDeposit,
        @DecimalMin(value = "0", message = "전용면적은 0 이상이어야 합니다.") @DecimalMax(value = "1000", message = "전용면적이 너무 큽니다.") Double maxExclusiveArea,
        @DecimalMin(value = "0", message = "최대 대출금 비율한도는 0 이상이어야 합니다.") @DecimalMax(value = "100", message = "최대 대출금 비율한도는 100% 이하로 입력해주세요.") Double maxLoanRatioPercent,
        @Min(value = 0, message = "최대 대출금액은 0 이상이어야 합니다.") @Max(value = 1_000_000, message = "최대 대출금액이 너무 큽니다.") Integer maxLoanAmount,
        Map<String, @Valid LoanPreference> preferences
) {
}
