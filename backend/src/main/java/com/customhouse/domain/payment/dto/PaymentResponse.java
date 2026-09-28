package com.customhouse.domain.payment.dto;

import com.customhouse.domain.payment.entity.Payment;

/**
 * [담당: 황진구] 결제 도메인 - 결제 결과 응답 DTO
 */
public record PaymentResponse(
        Long id,
        String orderId,
        String orderName,
        Long amount,
        String status
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getOrderName(),
                payment.getAmount(),
                payment.getStatus().name()
        );
    }
}
