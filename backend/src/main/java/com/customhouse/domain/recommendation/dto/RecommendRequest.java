package com.customhouse.domain.recommendation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - 요청 DTO
 * 프론트엔드(양혜승)가 입력받은 [연소득, 보증금, 희망 월세, 직장 위치]를 전달받는다.
 *
 * jobType/preferentialStatuses는 마이페이지 도메인의 enum(JobType/PreferentialStatus)과
 * 의미상 같은 값이지만, 여기서는 workLocation처럼 느슨한 String/List<String> pass-through로
 * 둔다 (진단은 비로그인 사용자도 쓸 수 있어 마이페이지 도메인과 결합하지 않는 기존 방식 유지).
 */
public record RecommendRequest(

        @NotNull(message = "소득(연소득, annualIncome)은 필수입니다.")
        @Min(value = 0, message = "연소득은 0 이상이어야 합니다.")
        Integer annualIncome, // 만원 단위 (연소득)

        @Min(value = 0, message = "부부합산 연소득은 0 이상이어야 합니다.")
        Integer coupleAnnualIncome, // 만원 단위, 선택. 있으면 annualIncome과 비교해 더 큰 값을 계산에 씀

        @NotNull(message = "보유 보증금(deposit)은 필수입니다.")
        @Min(value = 0, message = "보증금은 0 이상이어야 합니다.")
        Integer deposit, // 만원 단위, 현재 수중에 있는 현금

        @Min(value = 0, message = "희망 보증금/전세액은 0 이상이어야 합니다.")
        Integer desiredDeposit, // 만원 단위, 선택. 실제 계약에 쓸 목표 금액(대출 등으로 deposit보다 클 수 있음)

        @Min(value = 0, message = "희망 월세는 0 이상이어야 합니다.")
        Integer desiredRent, // 만원 단위, 선택 입력 (없으면 소득 기준 자동 산정)

        @NotBlank(message = "직장 위치(workLocation)는 필수입니다.")
        String workLocation, // 예: "강남구"

        Double workLat, // 선택. 카카오 주소검색으로 얻은 직장 정확한 위도 (있으면 이걸로 통근시간 계산)

        Double workLon, // 선택, workLat과 함께 옴

        @Min(value = 10, message = "희망 통근시간은 최소 10분 이상이어야 합니다.")
        Integer maxCommuteMinutes, // 선택, 기본값 40분

        Integer age, // 선택, 정부 지원정책 자격 판별용

        Boolean noHouseholder, // 선택, 무주택 세대주 여부 (버팀목 대출 자격 판별용)

        @Min(value = 0, message = "자산은 0 이상이어야 합니다.")
        Integer assets, // 만원 단위, 선택. 정책의 자산(순자산) 기준 판별용

        String jobType, // 선택. GOVERNMENT/SME/MID_SIZED/LARGE_CORP - 일부 정책의 직업종류 자격 판별용

        List<String> preferentialStatuses, // 선택. BASIC_LIVELIHOOD/NEAR_POVERTY/SINGLE_PARENT/
        // INDEPENDENT_YOUTH/NEWLYWED/MULTI_CHILD 중 다중 선택 - 일부 정책의 우대사항 자격 판별용

        String moveSchedule, // 선택. IMMEDIATE/WITHIN_3M/WITHIN_6M/EXPLORING - 아직 로직에 안 쓰고 AI 엔진으로 전달만 함 (추후 데이터 활용)

        String transportType, // 선택. PUBLIC/WALK/CAR - 아직 로직에 안 쓰고 AI 엔진으로 전달만 함 (추후 데이터 활용)

        Boolean useLoanPolicy, // 선택, 기본 true. false면 정부지원정책 목록에서 대출 상품을 추천하지 않음

        List<String> preferredBuildingTypes // 선택. 아파트/오피스텔/연립다세대/단독다가구 (실거래가 API 유형명 그대로).
        // 일부만 선택하면 그 유형의 매물만 추천, 비어 있거나 전부면 전체 추천
) {
}
