package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [담당: 허겸] 회원 도메인 - 비밀번호 변경 요청 (이메일 가입 계정 전용)
 */
public record ChangePasswordRequest(

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String newPassword
) {
}
