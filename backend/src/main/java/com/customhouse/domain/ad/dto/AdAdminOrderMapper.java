package com.customhouse.domain.ad.dto;

import com.customhouse.domain.ad.entity.AdOrder;
import com.customhouse.domain.ad.entity.ListingAd;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 광고 주문 + 그 매물의 광고 상태를 관리자 화면용 한 줄로 만든다.
 */
public final class AdAdminOrderMapper {

    private AdAdminOrderMapper() {
    }

    public static AdminAdOrderResponse toResponse(AdOrder o, String userEmail, ListingAd ad, LocalDateTime now) {
        return new AdminAdOrderResponse(o.getOrderId(), o.getUserId(), userEmail, o.getListingId(), o.getAmount(), o.getPeriodDays(),
                o.getStatus().name(), o.getFailReason(), o.getCreatedAt(),
                ad == null ? null : ad.getExpiresAt(), ad != null && ad.isActive(now));
    }
}
