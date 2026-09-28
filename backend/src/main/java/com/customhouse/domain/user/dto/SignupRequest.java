package com.customhouse.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 허겸] 회원 도메인 - 이메일 회원가입 요청 DTO
 * phone/marketingConsent는 선택 입력 - 비밀번호 재확인 일치 검증과 필수 약관 동의(이용약관/개인정보
 * 처리방침/위치기반서비스 이용약관/개인정보 제3자 제공 동의) 체크는 프론트에서 막고 여기엔 보내지 않는다
 * (서버는 아직 개별 약관 동의 이력을 저장하지 않는다).
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname,

        @Pattern(regexp = "^$|^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식으로 입력해주세요.")
        String phone,

        // Boolean(래퍼 타입)인 이유: 원시 boolean이면 Jackson 3가 이 필드 자체가 빠진 요청(예: 직접
        // API를 호출하는 다른 클라이언트)을 "필수값 누락"으로 보고 400을 낸다 (PostCreateRequest.anonymous와
        // 같은 문제). 실제 가입 폼(signup.html)은 항상 이 값을 보내지만, 안 보내도 기본값 false로 동작하게 한다.
        Boolean marketingConsent
) {
    public boolean isMarketingConsent() {
        return Boolean.TRUE.equals(marketingConsent);
    }
}
