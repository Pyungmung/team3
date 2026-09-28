package com.customhouse.domain.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 고객센터 - 주거정책 정보 정정신고 요청.
 * 리포트의 "주거정책 추천" 옆 "정책정보 정정신고" 팝업에서 보낸다. 로그인 여부와 상관없이 누구나 보낼 수 있다
 * (SecurityConfig에서 /api/support/** permitAll). 받는 사람은 클라이언트가 정할 수 없고 서버 설정(support.receiver-email)이다.
 *
 * correctionType은 정해진 코드만 받고(메일 제목에 들어가는 문구는 서버가 코드에서 만든다), 정책 관련 항목은 선택 입력이다
 * (특정 정책이 아니라 "누락된 정책 제보" 같은 경우 비워도 된다).
 */
public record PolicyCorrectionRequest(
        @NotBlank(message = "이름을 입력해주세요.") @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.") String name,
        @NotBlank(message = "답변받을 이메일을 입력해주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        @NotBlank(message = "정정 유형을 선택해주세요.")
        @Pattern(regexp = "INFO_ERROR|EXPIRED|MISSING|OTHER", message = "정정 유형이 올바르지 않습니다.") String correctionType,
        @Size(max = 40, message = "정책 ID가 너무 깁니다.") String policyId,
        @Size(max = 200, message = "정책명이 너무 깁니다.") String policyName,
        @Size(max = 100, message = "기관명이 너무 깁니다.") String policyAgency,
        @Size(max = 30, message = "지역값이 너무 깁니다.") String policyRegion,
        @Size(max = 500, message = "현재 표시된 내용이 너무 깁니다.") String currentDescription,
        @NotBlank(message = "정정할 내용을 입력해주세요.") @Size(max = 1500, message = "정정 내용은 1,500자 이하로 입력해주세요.") String message
) {
}
