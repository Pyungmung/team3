package com.customhouse.domain.policy.dto;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 목록 응답 (관심매물 페이지 "관심정책 조회" 표 한 줄). 정책의 "현재" 내용이다.
 */
public record FavoritePolicyResponse(
        Long policyId,
        String region,
        String agency,
        String name,
        String description,
        String link
) {
    public static FavoritePolicyResponse from(HousingPolicy p) {
        return new FavoritePolicyResponse(p.getId(), p.getRegion(), p.getAgency(), p.getName(), p.getDescription(), p.getLink());
    }
}
