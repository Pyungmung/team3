package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 허겸] 회원 도메인 - 마이페이지 회원정보 수정 요청 (이름/휴대폰). 이메일은 로그인 아이디라 바꿀 수 없다.
 * phone은 비워 보내면(null/빈 문자열) 저장된 번호를 지운다.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String nickname,

        @Pattern(regexp = "^$|^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식으로 입력해주세요.")
        String phone
) {
}
