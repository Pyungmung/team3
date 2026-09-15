package com.customhouse.domain.payment.dto;

import com.customhouse.domain.payment.entity.Subscription;

import java.time.LocalDate;

/**
 * [담당: 황진구] 결제 도메인 - 구독 상태 응답 DTO
 */
public record SubscriptionResponse(
        String status,
        LocalDate nextPaymentDate,
        LocalDate expiredAt
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getStatus().name(),
                subscription.getNextPaymentDate(),
                subscription.getExpiredAt()
        );
    }
}
