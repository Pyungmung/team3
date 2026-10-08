package com.customhouse.domain.housingpolicy.repository;

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [담당: 송귀성] 주거지원정책 JPA Repository
 */
public interface HousingPolicyRepository extends JpaRepository<HousingPolicy, Long> {

    /** 입력 순서(id 오름차순)가 곧 리포트 "주거정책 추천" 표의 표시 순서다 (예전 CSV 줄 순서와 같다). */
    List<HousingPolicy> findAllByOrderByIdAsc();
}
