package com.customhouse.domain.listing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * [담당: 송귀성] 회원 매물 등록 결과. AI 엔진이 정한 매물등록번호와 자치구를 그대로 돌려준다.
 * row는 AI 엔진이 저장한 CSV 행 전체(헤더 -> 값, 55컬럼)로, 백엔드가 DB(registered_listings.row_json)에 원본으로
 * 저장하는 데만 쓴다 - WRITE_ONLY라서 AI 엔진 응답에서는 읽히지만 프론트로 내보내는 응답에는 나가지 않는다.
 */
public record ListingRegistrationResponse(
        String listingId,
        String region,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) Map<String, Object> row
) {

    public ListingRegistrationResponse(String listingId, String region) {
        this(listingId, region, null);
    }
}
