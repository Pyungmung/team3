package com.customhouse.domain.appsetting.dto;

import com.customhouse.domain.appsetting.entity.AppSetting;

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 기타 설정 응답. 관리자 화면 표시용이자, AI 엔진 진단 요청에 그대로 실어 보내는 값이기도 하다
 * (AiListingRequest.appSettings 참고 - 필드명이 진단 요청 JSON의 camelCase 키와 그대로 대응한다).
 */
public record AppSettingResponse(
        Integer recommendationLimit,
        Integer adPriceWon,
        Integer adPeriodDays,
        LocalDateTime updatedAt
) {

    /** 광고 설정이 필요 없는 곳(테스트 등)용 - 광고 가격/기간은 기본값이다. */
    public AppSettingResponse(Integer recommendationLimit, LocalDateTime updatedAt) {
        this(recommendationLimit, AppSetting.DEFAULT_AD_PRICE_WON, AppSetting.DEFAULT_AD_PERIOD_DAYS, updatedAt);
    }
}
