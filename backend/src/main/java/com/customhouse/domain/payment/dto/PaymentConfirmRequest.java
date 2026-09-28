package com.customhouse.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * [담당: 황진구] 결제 도메인 - 단건 결제 승인 요청 DTO
 * 프론트엔드가 토스 결제창(SDK) successUrl로 돌아올 때 받은 쿼리 파라미터를 그대로 담아 보낸다.
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.")
        String paymentKey,

        @NotBlank(message = "orderId는 필수입니다.")
        String orderId,

        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 0보다 커야 합니다.")
        Long amount
) {
}
