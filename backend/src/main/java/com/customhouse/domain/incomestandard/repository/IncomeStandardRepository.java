package com.customhouse.domain.incomestandard.repository;

import com.customhouse.domain.incomestandard.entity.IncomeStandard;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [담당: 송귀성] 기준소득 통계(싱글톤 1행) JPA Repository. 조회는 IncomeStandard.SINGLETON_ID로 findById 한다.
 */
public interface IncomeStandardRepository extends JpaRepository<IncomeStandard, Long> {
}
