package com.customhouse.domain.listing.dto;

/**
 * [담당: 송귀성] 회원 매물 등록 결과. AI 엔진이 정한 매물등록번호와 자치구를 그대로 돌려준다.
 */
public record ListingRegistrationResponse(String listingId, String region) {
}
