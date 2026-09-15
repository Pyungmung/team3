package com.customhouse.domain.watchlist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 김시연] WatchList 도메인 - 관심 매물 등록 요청 DTO
 * 실제 매물 크롤링/외부 API 연동 전이라, 사용자가 직접 매물 정보를 입력해 등록한다.
 */
public record AddWatchlistRequest(
        @NotBlank(message = "주소(address)는 필수입니다.")
        String address,

        @NotBlank(message = "지역(region)은 필수입니다.")
        String region,

        @NotNull(message = "보증금(deposit)은 필수입니다.")
        @Min(value = 0, message = "보증금은 0 이상이어야 합니다.")
        Integer deposit,

        @NotNull(message = "월세(monthlyRent)는 필수입니다.")
        @Min(value = 0, message = "월세는 0 이상이어야 합니다.")
        Integer monthlyRent,

        @Min(value = 0, message = "관리비는 0 이상이어야 합니다.")
        Integer maintenanceFee,

        String sourceUrl
) {
}
