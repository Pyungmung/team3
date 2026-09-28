package com.customhouse.domain.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 추천 매물 - 관심매물 등록 요청. 마이페이지 목록에 그릴 정보를 담을 당시 값으로 함께 보낸다(스냅샷).
 */
public record ListingFavoriteRequest(
        @NotBlank(message = "매물번호가 필요합니다.")
        @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "매물번호가 올바르지 않습니다.") String listingId,
        @Size(max = 300, message = "주소가 너무 깁니다.") String address,
        @Size(max = 30, message = "지역값이 너무 깁니다.") String region,
        @Size(max = 10, message = "임대 유형이 올바르지 않습니다.") String leaseType,
        @Size(max = 100, message = "건물명이 너무 깁니다.") String buildingName,
        @Size(max = 30, message = "매물 유형이 너무 깁니다.") String propertyType,
        @Size(max = 60, message = "동호수 정보가 너무 깁니다.") String unitLabel,
        Integer deposit,
        Integer monthlyRent,
        Integer maintenanceFee,
        // 리포트 카드의 전체 매물 정보(JSON 문자열). 마이페이지에서 리포트와 같은 카드로 다시 그리는 데 쓴다.
        @Size(max = 30000, message = "매물 정보가 너무 큽니다.") String snapshot
) {
}
