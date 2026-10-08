package com.customhouse.domain.housingpolicy.dto;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 주거지원정책 1건 응답 (관리자 수정 > 주거지원정책 표의 한 줄)
 */
public record HousingPolicyResponse(
        Long id,
        String region,
        String agency,
        String name,
        String description,
        Integer minAge,
        Integer maxAge,
        Integer maxAnnualIncome,
        Integer maxAsset,
        Integer medianIncomePercent,
        boolean requireBasicLivelihood,
        boolean requireSme,
        boolean requireNewlywed,
        boolean requireNoHousehold,
        boolean loan,
        String note,
        String link,
        LocalDateTime updatedAt
) {
    public static HousingPolicyResponse from(HousingPolicy p) {
        return new HousingPolicyResponse(p.getId(), p.getRegion(), p.getAgency(), p.getName(), p.getDescription(),
                p.getMinAge(), p.getMaxAge(), p.getMaxAnnualIncome(), p.getMaxAsset(), p.getMedianIncomePercent(),
                p.isRequireBasicLivelihood(), p.isRequireSme(), p.isRequireNewlywed(), p.isRequireNoHousehold(),
                p.isLoan(), p.getNote(), p.getLink(), p.getUpdatedAt());
    }
}
