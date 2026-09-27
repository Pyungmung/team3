package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * [담당: 허겸] 회원 도메인 - 이메일 하나만 필요한 요청 공용 DTO
 * (회원가입 인증번호 발송 / 비밀번호 재설정 인증번호 발송에서 공용으로 사용)
 */
public record EmailRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
