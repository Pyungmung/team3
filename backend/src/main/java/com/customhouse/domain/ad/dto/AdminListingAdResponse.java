package com.customhouse.domain.ad.dto;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 관리자 광고 현황의 "광고 매물" 한 줄 (2026-10-08). 결제 주문과 별개로 지금 listing_ads에 있는 광고 전부를 보여 준다
 * (주문 없이 관리자가 접수한 예시 광고도 포함). lastOrderId가 주문번호(AD_...)가 아니면 결제 없이 접수된 광고다.
 */
public record AdminListingAdResponse(
        String listingId,
        Long userId,
        String userEmail,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        boolean active,
        String lastOrderId
) {
}
