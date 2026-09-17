package com.customhouse.domain.mypage.service;

import com.customhouse.domain.mypage.dto.MypageConditionRequest;
import com.customhouse.domain.mypage.dto.MypageConditionResponse;
import com.customhouse.domain.mypage.entity.HousingCondition;
import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * [담당: 황진구] 마이페이지 도메인 - 사용자 주거 조건 저장·조회·수정
 */
@Service
@RequiredArgsConstructor
public class MypageService {

    private final HousingConditionRepository housingConditionRepository;

    public MypageConditionResponse getMyCondition(Long userId) {
        HousingCondition condition = housingConditionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "저장된 주거 조건이 없습니다."));
        return MypageConditionResponse.from(condition);
    }

    /** 저장된 조건이 없으면 새로 만들고, 있으면 덮어쓴다 (upsert). */
    @Transactional
    public MypageConditionResponse saveMyCondition(Long userId, MypageConditionRequest request) {
        HousingCondition condition = housingConditionRepository.findByUserId(userId)
                .orElseGet(() -> HousingCondition.builder().userId(userId).build());

        condition.setAge(request.age());
        condition.setAnnualIncome(request.annualIncome());
        condition.setCoupleAnnualIncome(request.coupleAnnualIncome());
        condition.setWorkLocation(request.workLocation());
        condition.setDesiredDeposit(request.desiredDeposit());
        condition.setDesiredRent(request.desiredRent());
        condition.setRealEstateAsset(request.realEstateAsset());
        condition.setCarAsset(request.carAsset());
        condition.setFinancialAsset(request.financialAsset());
        condition.setOtherAsset(request.otherAsset());
        condition.setFinancialDebt(request.financialDebt());
        condition.setOtherDebt(request.otherDebt());
        condition.setJobType(request.jobType());
        condition.setNoHouseholder(request.noHouseholder());
        condition.setPreferentialStatuses(
                request.preferentialStatuses() != null ? request.preferentialStatuses() : Set.of()
        );
        condition.setNotificationEnabled(request.notificationEnabled());

        housingConditionRepository.save(condition);
        return MypageConditionResponse.from(condition);
    }
}
