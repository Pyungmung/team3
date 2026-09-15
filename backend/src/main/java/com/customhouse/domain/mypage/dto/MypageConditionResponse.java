package com.customhouse.domain.mypage.dto;

import com.customhouse.domain.mypage.entity.HousingCondition;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건 조회 응답 DTO
 */
public record MypageConditionResponse(
        Integer monthlyIncome,
        String workLocation,
        Integer desiredDeposit,
        Integer desiredRent,
        boolean notificationEnabled
) {
    public static MypageConditionResponse from(HousingCondition condition) {
        return new MypageConditionResponse(
                condition.getMonthlyIncome(),
                condition.getWorkLocation(),
                condition.getDesiredDeposit(),
                condition.getDesiredRent(),
                condition.isNotificationEnabled()
        );
    }
}
