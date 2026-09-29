package com.customhouse.domain.loan.repository;

import com.customhouse.domain.loan.entity.LoanReferenceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * [담당: 송귀성] 전세자금대출 참고 확인 페이지 주소 JPA Repository
 */
public interface LoanReferenceLinkRepository extends JpaRepository<LoanReferenceLink, Long> {

    Optional<LoanReferenceLink> findByLoanType(String loanType);

    List<LoanReferenceLink> findAllByLoanTypeIn(List<String> loanTypes);
}
