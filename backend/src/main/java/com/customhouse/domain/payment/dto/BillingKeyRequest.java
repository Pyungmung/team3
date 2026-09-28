package com.customhouse.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * [담당: 황진구] 결제 도메인 - 빌링키(자동결제 수단) 발급 요청 DTO
 * 프론트엔드가 payment.requestBillingAuth() 성공 후 successUrl 쿼리로 받은 customerKey/authKey를 담아 보낸다.
 */
public record BillingKeyRequest(
        @NotBlank(message = "customerKey는 필수입니다.")
        String customerKey,

        @NotBlank(message = "authKey는 필수입니다.")
        String authKey
) {
}
