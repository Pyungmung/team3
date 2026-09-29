package com.customhouse.domain.loan.repository;

import com.customhouse.domain.loan.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 송귀성] 전세자금대출 조건 JPA Repository
 */
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    Optional<LoanProduct> findByLoanType(String loanType);

    void deleteByLoanType(String loanType);
}
