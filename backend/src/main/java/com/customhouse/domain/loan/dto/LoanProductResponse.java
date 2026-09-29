package com.customhouse.domain.loan.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * [담당: 송귀성] 전세자금대출 조건 응답. 저장되지 않은 대출은 saved=false + 빈 조건으로 내려와 화면이 5개 탭을 항상 그릴 수 있다.
 * preferences에는 저장 여부와 관계없이 우대사항이 전부 들어 있다 (없으면 필수 아님 / 차감 0).
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
        Map<String, LoanPreference> preferences,
        LocalDateTime updatedAt,
        String referenceUrl
) {
}
