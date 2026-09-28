package com.customhouse.domain.listing.dto;

/**
 * [담당: 송귀성] 추천 매물 - 매물의 허위매물 신고 현황. flagged는 서버 기준(누적 신고 2건 이상)이라
 * 기준이 바뀌어도 프론트를 고칠 필요가 없다.
 */
public record ListingReportStatus(long count, boolean flagged) {
}
