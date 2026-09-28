package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * [담당: 허겸] 회원 도메인 - Access Token 재발급 요청 DTO
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
