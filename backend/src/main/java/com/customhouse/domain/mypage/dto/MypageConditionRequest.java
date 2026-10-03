package com.customhouse.domain.mypage.dto;

import com.customhouse.domain.mypage.entity.JobType;
import com.customhouse.domain.mypage.entity.PreferentialStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        @Size(max = 255, message = "직장 주소는 255자 이하여야 합니다.")
        String workAddress, // 선택. 카카오 주소검색으로 찾은 정확한 주소 (있으면 workLat/workLon과 함께 옴)

        Double workLat, // 선택. workAddress의 정확한 위도

        Double workLon, // 선택. workAddress의 정확한 경도

        @Min(value = 0, message = "현재 사용가능 보증금은 0 이상이어야 합니다.")
        Integer deposit, // 선택. 지금 수중에 있는 현금 기준 - AI 진단 폼의 deposit과 같은 값, 저장해두면 다음 진단 시 자동으로 불러온다

        @Min(value = 0, message = "희망 보증금은 0 이상이어야 합니다.")
        Integer desiredDeposit, // 선택. AI 진단 폼과 마찬가지로 미입력 시 연소득 기준으로 자동 계산됨

        @Min(value = 0, message = "희망 월세는 0 이상이어야 합니다.")
        Integer desiredRent,

        @Min(value = 0, message = "부동산 자산은 0 이상이어야 합니다.")
        Integer realEstateAsset,

        @Min(value = 0, message = "자동차 자산은 0 이상이어야 합니다.")
        Integer carAsset,

        @Min(value = 0, message = "금융자산은 0 이상이어야 합니다.")
        Integer financialAsset,

        @Min(value = 0, message = "금융부채는 0 이상이어야 합니다.")
        Integer financialDebt,

        JobType jobType,

        Boolean noHouseholder,

        @Min(value = 0, message = "병역이행기간은 0 이상이어야 합니다.")
        Integer militaryServiceMonths, // 개월 단위, 선택. 아직 어느 대출/정책 판별에도 쓰지 않는 값(필드만 수집) -
        // 추후 특정 대출상품에 연동 예정(12개월마다 가산연수 1년, 1개월만 초과해도 1년치 인정하는 식)

        @Min(value = 0, message = "대출접수일 기준 2년 내 출산한 자녀 수는 0 이상이어야 합니다.")
        Integer newbornAdditionalChildCount, // 선택. 신생아 특례 버팀목대출 전용 우대사항 판별용

        @Min(value = 0, message = "대출접수일 기준 출생 후 2년 초과한 미성년 자녀 수는 0 이상이어야 합니다.")
        Integer minorChildOver2YearsCount, // 선택. 신생아 특례 버팀목대출 전용 우대사항 판별용

        Set<PreferentialStatus> preferentialStatuses,

        boolean notificationEnabled
) {
}
