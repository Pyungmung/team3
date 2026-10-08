package com.customhouse.domain.ad.dto;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 광고하기 결제 승인 결과. registered가 true면 이 결제로 매물도 새로 등록된 것이다(매물 등록 + 광고하기 주문).
 */
public record AdConfirmResponse(String orderId, String listingId, boolean registered, long amount, int periodDays, LocalDateTime expiresAt) {
}
