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
 * [담당: 송귀성] 전세자금대출 조건 (관리자 수정 > 전세자금대출). 대출 종류(LoanType)마다 1행이라 (loan_type)이 유니크다.
 * 금액은 만원 단위, 값이 null이면 그 조건은 "제한 없음"이다.
 * 우대사항(필수 여부 + 우대금리 차감)은 항목이 늘어날 수 있어 JSON 문자열(preferences)로 저장한다 (LoanPreferenceKey 참고).
 * 이후 이자 계산식이 이 조건으로 자격을 판별하고 우대금리를 계산한다 (다음 단계).
 */
@Entity
@Table(name = "loan_products",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_product_type", columnNames = "loan_type"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_type", nullable = false, length = 30)
    private String loanType;

    /** 나이(만) 최소 / 최대 */
    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    /** 연소득 이하 (만원): 개인 / 부부합산 */
    @Column(name = "max_income_single")
    private Integer maxIncomeSingle;

    @Column(name = "max_income_couple")
    private Integer maxIncomeCouple;

    /** 총자산 이하 (만원) */
    @Column(name = "max_asset")
    private Integer maxAsset;

    /** 매물 보증금 이하 (만원): 이 금액을 넘는 매물에는 이 대출을 적용하지 않는다 */
    @Column(name = "max_listing_deposit")
    private Integer maxListingDeposit;

    /** 전용면적 이하 (㎡) */
    @Column(name = "max_exclusive_area")
    private Double maxExclusiveArea;

    /** 최대 대출금 비율한도 (%): 매물 보증금 중 이 비율까지만 대출 가능 (예: 80이면 보증금의 80%까지) */
    @Column(name = "max_loan_ratio_percent")
    private Double maxLoanRatioPercent;

    /** 최대 대출금액 (만원): 매물과 무관하게 이 대출로 빌릴 수 있는 절대 상한 */
    @Column(name = "max_loan_amount")
    private Integer maxLoanAmount;

    /** 우대사항 JSON: {"NEWLYWED": {"required": false, "discount": 0.2}, ...} */
    @Column(columnDefinition = "TEXT")
    private String preferences;

    public static LoanProduct of(LoanType type) {
        LoanProduct p = new LoanProduct();
        p.loanType = type.name();
        return p;
    }

    public void update(Integer minAge, Integer maxAge, Integer maxIncomeSingle, Integer maxIncomeCouple,
                       Integer maxAsset, Integer maxListingDeposit, Double maxExclusiveArea,
                       Double maxLoanRatioPercent, Integer maxLoanAmount, String preferences) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.maxIncomeSingle = maxIncomeSingle;
        this.maxIncomeCouple = maxIncomeCouple;
        this.maxAsset = maxAsset;
        this.maxListingDeposit = maxListingDeposit;
        this.maxExclusiveArea = maxExclusiveArea;
        this.maxLoanRatioPercent = maxLoanRatioPercent;
        this.maxLoanAmount = maxLoanAmount;
        this.preferences = preferences;
    }
}
