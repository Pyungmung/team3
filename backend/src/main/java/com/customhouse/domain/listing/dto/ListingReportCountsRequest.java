package com.customhouse.domain.listing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * [담당: 송귀성] 추천 매물 - 매물별 허위매물 신고 수 조회 요청 (카드 목록을 그릴 때 한 번에 조회).
 */
public record ListingReportCountsRequest(
        @NotEmpty(message = "매물번호를 보내주세요.") @Size(max = 200, message = "한 번에 200개까지 조회할 수 있습니다.") List<String> listingIds
) {
}
