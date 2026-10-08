package com.customhouse.domain.ad.dto;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 관리자 광고 현황의 주문 한 줄 (관리자 수정 > 광고 현황). adExpiresAt은 그 매물 광고의 현재 만료일(없으면 null).
 */
public record AdminAdOrderResponse(
        String orderId,
        Long userId,
        String userEmail,
        String listingId,
        long amount,
        int periodDays,
        String status,
        String failReason,
        LocalDateTime createdAt,
        LocalDateTime adExpiresAt,
        boolean adActive
) {
}
