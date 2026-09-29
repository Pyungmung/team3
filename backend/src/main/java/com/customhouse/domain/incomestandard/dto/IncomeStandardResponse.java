package com.customhouse.domain.incomestandard.dto;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 기준소득 통계 응답. 관리자 화면 표시용이자, AI 엔진 진단 요청에 그대로 실어 보내는 값이기도 하다
 * (AiListingRequest.incomeStandard 참고 - 필드명이 이 진단 요청 JSON의 camelCase 키와 그대로 대응한다).
 */
public record IncomeStandardResponse(
        Double rirOverallPercent,
        Double rirMetroPercent,
        Double rirLowPercent,
        Double rirMidPercent,
        Double rirHighPercent,
        Integer rirYear,
        String rirSource,
        Long medianIncome100PercentMonthly,
        LocalDateTime updatedAt
) {
}
