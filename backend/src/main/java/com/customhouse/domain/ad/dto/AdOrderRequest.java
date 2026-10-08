package com.customhouse.domain.ad.dto;

import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * [담당: 송귀성] 광고하기 주문 요청. 둘 중 하나만 보낸다:
 * - listingId: 이미 등록한 내 매물을 광고 (마이페이지 "광고하기")
 * - listing: 등록 폼 내용 - 결제가 끝나면 서버가 매물을 등록하고 광고까지 접수 (등록 페이지 "광고하기 매물등록").
 *   폼 내용은 매물 등록 때와 같은 검증을 이 단계에서 먼저 받는다(결제 후에 검증 오류로 실패하지 않도록).
 */
public record AdOrderRequest(
        @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "매물번호가 올바르지 않습니다.") String listingId,
        @Valid ListingRegistrationRequest listing
) {

    @AssertTrue(message = "광고할 매물번호 또는 등록할 매물 내용 중 하나만 보내주세요.")
    public boolean isExactlyOneTarget() {
        return (listingId != null && !listingId.isBlank()) != (listing != null);
    }
}
