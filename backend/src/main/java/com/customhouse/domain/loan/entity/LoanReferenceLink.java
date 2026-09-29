package com.customhouse.domain.loan.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 전세자금대출 참고 확인 페이지 주소 (관리자 수정 > 전세자금대출, 대출별 "참고 확인 페이지" 입력).
 * 계산 로직과는 전혀 무관한, 관리자가 그 대출을 조사할 때 참고한 홈페이지 주소를 저장만 해두는 용도다.
 * LoanProduct(자격 조건)와 일부러 같은 테이블/엔티티에 두지 않는다 - LoanProduct 행이 있으면 "저장됨"(saved=true)으로
 * 보고 리포트 추천에 그 대출을 적용하는데, 조건 없이 참고 링크만 저장했다고 그 대출이 모든 매물에 적용되면 안 되기 때문이다.
 */
@Entity
@Table(name = "loan_reference_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_reference_link_type", columnNames = "loan_type"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanReferenceLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_type", nullable = false, length = 30)
    private String loanType;

    @Column(name = "reference_url", length = 500)
    private String referenceUrl;

    public static LoanReferenceLink of(LoanType type, String referenceUrl) {
        LoanReferenceLink link = new LoanReferenceLink();
        link.loanType = type.name();
        link.referenceUrl = referenceUrl;
        return link;
    }

    public void updateUrl(String referenceUrl) {
        this.referenceUrl = referenceUrl;
    }
}
