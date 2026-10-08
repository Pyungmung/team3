package com.customhouse.domain.policy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 담기 요청. 정책 내용은 서버(housing_policies)가 알고 있으므로 정책 id만 받는다.
 */
public record FavoritePolicyRequest(
        @NotNull(message = "정책 번호가 필요합니다.")
        @Positive(message = "정책 번호가 올바르지 않습니다.") Long policyId
) {
}
