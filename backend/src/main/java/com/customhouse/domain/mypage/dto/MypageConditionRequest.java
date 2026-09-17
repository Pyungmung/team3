package com.customhouse.domain.mypage.dto;

import com.customhouse.domain.mypage.entity.JobType;
import com.customhouse.domain.mypage.entity.PreferentialStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건 저장/수정 요청 DTO
 * AI 진단 폼(domain/recommendation)과 같은 형태의 입력값을 마이페이지에도 저장해서,
 * 다음 번 진단 시 자동으로 불러와 쓸 수 있게 한다. 청년 주거지원 정책 매칭에 쓰이는
 * 프로필(나이/소득/자산/직업종류/무주택여부/우대사항)도 함께 저장한다.
 */
public record MypageConditionRequest(

        @Min(value = 0, message = "나이는 0 이상이어야 합니다.")
        @Max(value = 120, message = "나이는 120 이하여야 합니다.")
        Integer age,

        @NotNull(message = "소득(연소득, annualIncome)은 필수입니다.")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다.")
        Integer annualIncome,

        @Min(value = 0, message = "부부합산 연소득은 0 이상이어야 합니다.")
        Integer coupleAnnualIncome,

        @NotBlank(message = "직장 위치(workLocation)는 필수입니다.")
        String workLocation,

        @NotNull(message = "희망 보증금(desiredDeposit)은 필수입니다.")
        @Min(value = 0, message = "희망 보증금은 0 이상이어야 합니다.")
        Integer desiredDeposit,

        @Min(value = 0, message = "희망 월세는 0 이상이어야 합니다.")
        Integer desiredRent,

        @Min(value = 0, message = "부동산 자산은 0 이상이어야 합니다.")
        Integer realEstateAsset,

        @Min(value = 0, message = "자동차 자산은 0 이상이어야 합니다.")
        Integer carAsset,

        @Min(value = 0, message = "금융자산은 0 이상이어야 합니다.")
        Integer financialAsset,

        @Min(value = 0, message = "일반자산은 0 이상이어야 합니다.")
        Integer otherAsset,

        @Min(value = 0, message = "금융부채는 0 이상이어야 합니다.")
        Integer financialDebt,

        @Min(value = 0, message = "일반부채는 0 이상이어야 합니다.")
        Integer otherDebt,

        JobType jobType,

        Boolean noHouseholder,

        Set<PreferentialStatus> preferentialStatuses,

        boolean notificationEnabled
) {
}
