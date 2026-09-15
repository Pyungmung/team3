package com.customhouse.domain.mypage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건 저장/수정 요청 DTO
 * AI 진단 폼(domain/recommendation)과 같은 형태의 입력값을 마이페이지에도 저장해서,
 * 다음 번 진단 시 자동으로 불러와 쓸 수 있게 한다.
 */
public record MypageConditionRequest(

        @NotNull(message = "월급(monthlyIncome)은 필수입니다.")
        @Min(value = 0, message = "월급은 0 이상이어야 합니다.")
        Integer monthlyIncome,

        @NotBlank(message = "직장 위치(workLocation)는 필수입니다.")
        String workLocation,

        @NotNull(message = "희망 보증금(desiredDeposit)은 필수입니다.")
        @Min(value = 0, message = "희망 보증금은 0 이상이어야 합니다.")
        Integer desiredDeposit,

        @Min(value = 0, message = "희망 월세는 0 이상이어야 합니다.")
        Integer desiredRent,

        boolean notificationEnabled
) {
}
