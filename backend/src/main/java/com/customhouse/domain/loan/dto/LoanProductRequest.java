package com.customhouse.domain.loan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] 전세자금대출 조건 저장 요청 (관리자 화면의 "수정완료"). 금액은 만원 단위, null은 "제한 없음".
 * preferences의 키는 LoanPreferenceKey에 있는 항목만 허용한다 (서비스에서 확인).
 * rateTable은 부부합산 연소득 4구간 x 임차보증금 3구간(총 12칸)의 연 금리(%) 표 - null이면 이 대출은 아직
 * 실제 금리표가 없어 임시 고정금리를 쓴다. 있으면 12칸(4행 x 3열)을 정확히 채워야 한다(서비스에서 확인).
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

        @Min(value = 0, message = "매물 월세 제한은 0 이상이어야 합니다.") @Max(value = 10_000, message = "매물 월세 제한이 너무 큽니다.") Integer maxListingMonthlyRent,

        // 청년전용 보증부월세대출 "전용" 금리 구조 - 다른 대출은 전부 null로 둔다.
        @DecimalMin(value = "0", message = "보증금 대출 금리는 0 이상이어야 합니다.") @DecimalMax(value = "15", message = "보증금 대출 금리가 너무 큽니다.") Double depositLoanRatePercent,
        @Min(value = 0, message = "월세대출 월 한도는 0 이상이어야 합니다.") @Max(value = 1_000, message = "월세대출 월 한도가 너무 큽니다.") Integer monthlyRentLoanCapManwon,
        @Min(value = 0, message = "월세대출 무이자 기준액은 0 이상이어야 합니다.") @Max(value = 1_000, message = "월세대출 무이자 기준액이 너무 큽니다.") Integer monthlyRentLoanFreeThresholdManwon,
        @DecimalMin(value = "0", message = "월세대출 금리는 0 이상이어야 합니다.") @DecimalMax(value = "15", message = "월세대출 금리가 너무 큽니다.") Double monthlyRentLoanRatePercent,

        Map<String, @Valid LoanPreference> preferences,
        List<List<Double>> rateTable
) {
}
