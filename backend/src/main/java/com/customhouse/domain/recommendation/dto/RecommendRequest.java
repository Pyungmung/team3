package com.customhouse.domain.recommendation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - 요청 DTO
 * 프론트엔드(양혜승)가 입력받은 [월급, 보증금, 희망 월세, 직장 위치]를 전달받는다.
 */
public record RecommendRequest(

        @NotNull(message = "월급(monthlyIncome)은 필수입니다.")
        @Min(value = 0, message = "월급은 0 이상이어야 합니다.")
        Integer monthlyIncome, // 만원 단위

        @NotNull(message = "보유 보증금(deposit)은 필수입니다.")
        @Min(value = 0, message = "보증금은 0 이상이어야 합니다.")
        Integer deposit, // 만원 단위

        @Min(value = 0, message = "희망 월세는 0 이상이어야 합니다.")
        Integer desiredRent, // 만원 단위, 선택 입력 (없으면 소득 기준 자동 산정)

        @NotBlank(message = "직장 위치(workLocation)는 필수입니다.")
        String workLocation, // 예: "강남구"

        @Min(value = 10, message = "희망 통근시간은 최소 10분 이상이어야 합니다.")
        Integer maxCommuteMinutes, // 선택, 기본값 40분

        Integer age, // 선택, 정부 지원정책 자격 판별용

        Boolean noHouseholder // 선택, 무주택 세대주 여부 (버팀목 대출 자격 판별용)
) {
}
