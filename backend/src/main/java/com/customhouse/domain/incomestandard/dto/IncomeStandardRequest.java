package com.customhouse.domain.incomestandard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * [담당: 송귀성] 기준소득 통계 저장 요청 (관리자 수정 > 기준소득관리). RIR은 % 단위, 기준중위소득은 원 단위(1원까지 정확히) 정수다.
 */
public record IncomeStandardRequest(
        @DecimalMin(value = "0", message = "전국 RIR은 0% 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "전국 RIR은 100% 이하로 입력해주세요.") Double rirOverallPercent,
        @DecimalMin(value = "0", message = "수도권 RIR은 0% 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "수도권 RIR은 100% 이하로 입력해주세요.") Double rirMetroPercent,
        @DecimalMin(value = "0", message = "하위 RIR은 0% 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "하위 RIR은 100% 이하로 입력해주세요.") Double rirLowPercent,
        @DecimalMin(value = "0", message = "중위 RIR은 0% 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "중위 RIR은 100% 이하로 입력해주세요.") Double rirMidPercent,
        @DecimalMin(value = "0", message = "상위 RIR은 0% 이상이어야 합니다.")
        @DecimalMax(value = "100", message = "상위 RIR은 100% 이하로 입력해주세요.") Double rirHighPercent,
        @Min(value = 1900, message = "기준연도가 올바르지 않습니다.") Integer rirYear,
        String rirSource,
        @Min(value = 0, message = "기준중위소득은 0 이상이어야 합니다.") Long medianIncome100PercentMonthly
) {
}
