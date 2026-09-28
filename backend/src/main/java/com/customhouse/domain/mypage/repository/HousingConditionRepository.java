package com.customhouse.domain.mypage.repository;

import com.customhouse.domain.mypage.entity.HousingCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 황진구] 마이페이지 도메인 - HousingCondition JPA Repository
 */
public interface HousingConditionRepository extends JpaRepository<HousingCondition, Long> {

    Optional<HousingCondition> findByUserId(Long userId);
}
