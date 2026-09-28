package com.customhouse.domain.listing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 요청. 로그인한 회원만 보낼 수 있다 (신고자 이름/이메일은 회원 정보를 쓴다).
 * 매물 요약(address, leaseType, deposit, monthlyRent)은 관리자 메일 본문에 참고용으로만 들어간다 (서버는 CSV를 읽지 않는다).
 * reportType은 정해진 코드만 받고, 메일 제목의 문구는 서버가 코드에서 만든다.
 */
public record ListingReportRequest(
        @NotBlank(message = "신고 유형을 선택해주세요.")
        @Pattern(regexp = "FAKE_PRICE|ALREADY_SOLD|WRONG_INFO|PHOTO_MISMATCH|OTHER", message = "신고 유형이 올바르지 않습니다.") String reportType,
        @NotBlank(message = "신고 내용을 입력해주세요.") @Size(max = 1500, message = "신고 내용은 1,500자 이하로 입력해주세요.") String message,
        @Email(message = "이메일 형식이 올바르지 않습니다.") @Size(max = 191, message = "이메일이 너무 깁니다.") String contactEmail,
        @Size(max = 300, message = "주소가 너무 깁니다.") String address,
        @Size(max = 10, message = "임대 유형이 올바르지 않습니다.") String leaseType,
        Integer deposit,
        Integer monthlyRent
) {
}
