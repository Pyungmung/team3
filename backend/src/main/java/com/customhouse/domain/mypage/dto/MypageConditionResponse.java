package com.customhouse.domain.mypage.dto;

import com.customhouse.domain.mypage.entity.HousingCondition;
import com.customhouse.domain.mypage.entity.JobType;
import com.customhouse.domain.mypage.entity.PreferentialStatus;

import java.util.Set;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건 조회 응답 DTO
 * netAsset은 저장된 컬럼이 아니라 HousingCondition.getNetAsset()의 계산값이다.
 */
public record MypageConditionResponse(
        Integer age,
        Integer annualIncome,
        Integer coupleAnnualIncome,
        String workLocation,
        Integer desiredDeposit,
        Integer desiredRent,
        Integer realEstateAsset,
        Integer carAsset,
        Integer financialAsset,
        Integer otherAsset,
        Integer financialDebt,
        Integer otherDebt,
        Integer netAsset,
        JobType jobType,
        Boolean noHouseholder,
        Set<PreferentialStatus> preferentialStatuses,
        boolean notificationEnabled
) {
    public static MypageConditionResponse from(HousingCondition condition) {
        return new MypageConditionResponse(
                condition.getAge(),
                condition.getAnnualIncome(),
                condition.getCoupleAnnualIncome(),
                condition.getWorkLocation(),
                condition.getDesiredDeposit(),
                condition.getDesiredRent(),
                condition.getRealEstateAsset(),
                condition.getCarAsset(),
                condition.getFinancialAsset(),
                condition.getOtherAsset(),
                condition.getFinancialDebt(),
                condition.getOtherDebt(),
                condition.getNetAsset(),
                condition.getJobType(),
                condition.getNoHouseholder(),
                condition.getPreferentialStatuses(),
                condition.isNotificationEnabled()
        );
    }
}
