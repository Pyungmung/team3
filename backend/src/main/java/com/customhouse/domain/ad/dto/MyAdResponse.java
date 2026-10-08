package com.customhouse.domain.ad.dto;

import com.customhouse.domain.ad.entity.ListingAd;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 내 광고 현황 한 줄 - 마이페이지 "등록한 매물 관리"에서 "광고중 · D-N"과 연장 버튼에 쓴다.
 * remainingDays는 남은 시간을 올림한 일수(오늘 끝나면 1).
 */
public record MyAdResponse(String listingId, boolean active, LocalDateTime expiresAt, long remainingDays) {

    public static MyAdResponse of(ListingAd ad, LocalDateTime now) {
        boolean active = ad.isActive(now);
        long remaining = 0;
        if (active) {
            long minutes = Duration.between(now, ad.getExpiresAt()).toMinutes();
            remaining = Math.max(1, (minutes + 1439) / 1440);
        }
        return new MyAdResponse(ad.getListingId(), active, ad.getExpiresAt(), remaining);
    }
}
