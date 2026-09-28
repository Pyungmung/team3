package com.customhouse.domain.watchlist.dto;

import com.customhouse.domain.watchlist.entity.Property;
import com.customhouse.domain.watchlist.entity.WatchlistItem;

/**
 * [담당: 김시연] WatchList 도메인 - 관심 매물 목록 응답 DTO (WatchlistItem + Property 정보 합침)
 */
public record WatchlistItemResponse(
        Long watchlistItemId,
        Long propertyId,
        String address,
        String region,
        Integer deposit,
        Integer monthlyRent,
        Integer maintenanceFee,
        String sourceUrl,
        String status,
        int reportCount
) {
    public static WatchlistItemResponse of(WatchlistItem item, Property property) {
        return new WatchlistItemResponse(
                item.getId(),
                property.getId(),
                property.getAddress(),
                property.getRegion(),
                property.getDeposit(),
                property.getMonthlyRent(),
                property.getMaintenanceFee(),
                property.getSourceUrl(),
                item.getStatus().name(),
                property.getReportCount()
        );
    }
}
