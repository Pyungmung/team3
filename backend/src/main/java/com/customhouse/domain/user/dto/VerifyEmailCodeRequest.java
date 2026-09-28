package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * [담당: 허겸] 회원 도메인 - 회원가입 이메일 인증번호 확인 요청 DTO
 */
public record VerifyEmailCodeRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
        String code
) {
}
