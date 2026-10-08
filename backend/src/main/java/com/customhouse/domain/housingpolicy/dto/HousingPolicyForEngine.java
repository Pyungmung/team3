package com.customhouse.domain.housingpolicy.dto;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;

/**
 * [담당: 송귀성] AI 엔진(customhouse-ai)에 진단 요청과 함께 실어 보내는 정책 1건 - 매칭에 필요한 값만 담는다(관리자용 메모/수정 시각 제외).
 * AI 엔진의 HousingPolicyCondition(request_schema.py)과 필드 이름이 같아야 한다. 캐시에 담기므로 바뀌지 않는 record다.
 */
public record HousingPolicyForEngine(
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
        String link
) {
    public static HousingPolicyForEngine from(HousingPolicy p) {
        return new HousingPolicyForEngine(p.getId(), p.getRegion(), p.getAgency(), p.getName(), p.getDescription(),
                p.getMinAge(), p.getMaxAge(), p.getMaxAnnualIncome(), p.getMaxAsset(), p.getMedianIncomePercent(),
                p.isRequireBasicLivelihood(), p.isRequireSme(), p.isRequireNewlywed(), p.isRequireNoHousehold(),
                p.isLoan(), p.getLink());
    }
}
