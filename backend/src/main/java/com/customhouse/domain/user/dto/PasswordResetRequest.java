package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 허겸] 회원 도메인 - 비밀번호 재설정 요청 DTO
 * 이메일 인증번호 확인과 새 비밀번호 설정을 한 번에 처리한다 (별도 재설정 토큰 발급 없이 바로 반영).
 */
public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다.")
        String code,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String newPassword
) {
}
