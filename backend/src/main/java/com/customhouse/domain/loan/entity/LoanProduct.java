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

    /** 매물 월세 제한 (이하, 만원): 이 금액을 넘는 매물에는 이 대출을 적용하지 않는다. 전세 대출은 매물에
     * 월세가 없어 의미가 없고, 청년전용 보증부월세대출처럼 월세 매물을 대상으로 하는 대출에서만 쓴다
     * (공통 조건이 아니라 이 대출만의 별도 조건 - 관리자 화면에서도 따로 한 줄로 보여준다). */
    @Column(name = "max_listing_monthly_rent")
    private Integer maxListingMonthlyRent;

    /** 2026-10-02: 청년전용 보증부월세대출 "전용" 금리 구조 - 보증금 대출과 월세대출을 따로 심사한다
     * (일반적인 연소득x임차보증금 구간표 방식이 아니다). 다른 대출 종류에는 의미 없는 값이라 전부 null로 둔다.
     * 보증금 대출 금리(연 %, 고정값 - 표 대신 이 값을 기본금리로 쓴다. _table_base_rate_percent 참고). */
    @Column(name = "deposit_loan_rate_percent")
    private Double depositLoanRatePercent;

    /** 월세대출 월 한도(만원) - 실제 월세가 이 금액을 넘으면 이 금액까지만 대출로 메운다. AI 엔진이 2년(24개월)
     * 고정 가정으로 전체 한도(안내용, 예: 50만원 x 24개월 = 1200만원)를 계산할 때도 이 값을 쓴다(기간은
     * 입력칸이 아니라 코드 고정값 - 실제 거주 기간을 알 방법이 없어 표준 임대 기간 2년을 그냥 가정한다). */
    @Column(name = "monthly_rent_loan_cap_manwon")
    private Integer monthlyRentLoanCapManwon;

    /** 월세대출 무이자 기준액(만원) - 월세 중 이 금액까지는 무이자, 넘는 금액부터만 이자가 붙는다. */
    @Column(name = "monthly_rent_loan_free_threshold_manwon")
    private Integer monthlyRentLoanFreeThresholdManwon;

    /** 월세대출 무이자 기준액 초과분에 적용되는 금리(연 %). */
    @Column(name = "monthly_rent_loan_rate_percent")
    private Double monthlyRentLoanRatePercent;

    /** 우대사항 JSON: {"NEWLYWED": {"required": false, "discount": 0.2}, ...} */
    @Column(columnDefinition = "TEXT")
    private String preferences;

    /** 대출금리표 JSON: [[행0(연소득 ~2천만원 이하) 3칸], [행1(2천~4천)], [행2(4천~6천)], [행3(6천~7.5천)]] -
     * 행은 부부합산 연소득 구간(4개, RateTableService.INCOME_BRACKETS_MANWON 순서 고정), 열은 임차보증금 구간
     * (3개, DEPOSIT_BRACKETS_MANWON 고정) 순서다. null이면 이 대출은 아직 실제 금리표가 없어 임시 고정금리
     * (loan_matcher.DEFAULT_BASE_RATE_PERCENT)를 쓴다. */
    @Column(columnDefinition = "TEXT")
    private String rateTable;

    public static LoanProduct of(LoanType type) {
        LoanProduct p = new LoanProduct();
        p.loanType = type.name();
        return p;
    }

    public void update(Integer minAge, Integer maxAge, Integer maxIncomeSingle, Integer maxIncomeCouple,
                       Integer maxAsset, Integer maxListingDeposit, Double maxExclusiveArea,
                       Double maxLoanRatioPercent, Integer maxLoanAmount, Integer maxListingMonthlyRent,
                       Double depositLoanRatePercent, Integer monthlyRentLoanCapManwon,
                       Integer monthlyRentLoanFreeThresholdManwon, Double monthlyRentLoanRatePercent,
                       String preferences, String rateTable) {
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.maxIncomeSingle = maxIncomeSingle;
        this.maxIncomeCouple = maxIncomeCouple;
        this.maxAsset = maxAsset;
        this.maxListingDeposit = maxListingDeposit;
        this.maxExclusiveArea = maxExclusiveArea;
        this.maxLoanRatioPercent = maxLoanRatioPercent;
        this.maxLoanAmount = maxLoanAmount;
        this.maxListingMonthlyRent = maxListingMonthlyRent;
        this.depositLoanRatePercent = depositLoanRatePercent;
        this.monthlyRentLoanCapManwon = monthlyRentLoanCapManwon;
        this.monthlyRentLoanFreeThresholdManwon = monthlyRentLoanFreeThresholdManwon;
        this.monthlyRentLoanRatePercent = monthlyRentLoanRatePercent;
        this.preferences = preferences;
        this.rateTable = rateTable;
    }
}
