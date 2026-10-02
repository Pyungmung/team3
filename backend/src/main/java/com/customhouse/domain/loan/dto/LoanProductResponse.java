package com.customhouse.domain.loan.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] 전세자금대출 조건 응답. 저장되지 않은 대출은 saved=false + 빈 조건으로 내려와 화면이 5개 탭을 항상 그릴 수 있다.
 * preferences에는 저장 여부와 관계없이 우대사항이 전부 들어 있다 (없으면 필수 아님 / 차감 0).
 * rateTable이 null이면 이 대출은 아직 실제 금리표가 없어 임시 고정금리(연 3%)를 쓴다 - LoanProductRequest 참고.
 */
public record LoanProductResponse(
        String type,
        String name,
        String leaseType,
        boolean saved,
        Integer minAge,
        Integer maxAge,
        Integer maxIncomeSingle,
        Integer maxIncomeCouple,
        Integer maxAsset,
        Integer maxListingDeposit,
        Double maxExclusiveArea,
        Double maxLoanRatioPercent,
        Integer maxLoanAmount,
        Integer maxListingMonthlyRent,
        Double depositLoanRatePercent,
        Integer monthlyRentLoanCapManwon,
        Integer monthlyRentLoanFreeThresholdManwon,
        Double monthlyRentLoanRatePercent,
        Map<String, LoanPreference> preferences,
        List<List<Double>> rateTable,
        LocalDateTime updatedAt,
        String referenceUrl
) {
}
