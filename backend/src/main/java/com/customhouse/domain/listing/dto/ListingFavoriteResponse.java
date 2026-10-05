package com.customhouse.domain.listing.dto;

import com.customhouse.domain.listing.entity.ListingFavorite;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 추천 매물 - 마이페이지 관심매물 목록 응답 (허위매물 신고 현황 포함).
 */
public record ListingFavoriteResponse(
        String listingId,
        String address,
        String region,
        String leaseType,
        String buildingName,
        String propertyType,
        String unitLabel,
        Integer deposit,
        Integer monthlyRent,
        Integer maintenanceFee,
        long reportCount,
        boolean flagged,
        String snapshot,
        Integer previousDeposit,
        Integer previousMonthlyRent,
        LocalDateTime priceChangedAt
) {
    public static ListingFavoriteResponse of(ListingFavorite fav, ListingReportStatus status) {
        return new ListingFavoriteResponse(fav.getListingId(), fav.getAddress(), fav.getRegion(), fav.getLeaseType(),
                fav.getBuildingName(), fav.getPropertyType(), fav.getUnitLabel(),
                fav.getDeposit(), fav.getMonthlyRent(), fav.getMaintenanceFee(),
                status.count(), status.flagged(), fav.getSnapshot(),
                fav.getPreviousDeposit(), fav.getPreviousMonthlyRent(), fav.getPriceChangedAt());
    }
}
