package com.customhouse.domain.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [담당: 미정] 고객센터 - 이메일 문의 요청.
 * 로그인 여부와 상관없이 누구나 보낼 수 있다 (SecurityConfig에서 /api/support/** permitAll).
 */
public record InquiryRequest(
        @NotBlank(message = "이름을 입력해주세요.") @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.") String name,
        @NotBlank(message = "답변받을 이메일을 입력해주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        @NotBlank(message = "문의 내용을 입력해주세요.") @Size(max = 2000, message = "문의 내용은 2,000자 이하로 입력해주세요.") String message
) {
}
