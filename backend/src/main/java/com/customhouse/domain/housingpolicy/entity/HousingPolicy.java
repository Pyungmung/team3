package com.customhouse.domain.housingpolicy.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 주거지원정책 1건 (2026-10-08, docs/housing_policy_list.csv + policies.json을 DB로 옮김).
 * 관리자 수정 > 주거지원정책 탭에서 수정/추가/삭제하고, 진단 요청 때 백엔드가 이 목록을 AI 엔진에 실어 보내 리포트 "주거정책 추천"에 쓰인다.
 * 관심정책(FavoritePolicy)은 이 테이블의 id만 들고 있어서, 여기서 고치면 관심정책 조회에도 바로 반영된다.
 * 금액은 만원 단위, 나이는 만 나이. 조건 값이 null/false면 "제한 없음"이다. 도메인 간 참조는 FK 없이 id만 쓴다.
 */
@Entity
@Table(name = "housing_policies", indexes = @Index(name = "idx_housing_policy_region", columnList = "region"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HousingPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "서울"이면 서울 전역(25개 자치구 모두) 공통 정책, 자치구 이름(예: 강남구)이면 그 자치구 정책 */
    @Column(nullable = false, length = 30)
    private String region;

    /** 소관 기관명 */
    @Column(nullable = false, length = 100)
    private String agency;

    @Column(nullable = false, length = 150)
    private String name;

    /** 지원혜택 내용 (자유 텍스트) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    /** 개인/부부합산 연소득 상한(만원) */
    @Column(name = "max_annual_income")
    private Integer maxAnnualIncome;

    /** 총자산 상한(만원) */
    @Column(name = "max_asset")
    private Integer maxAsset;

    /** 기준중위소득 비율 상한(%) - 기준소득관리의 기준중위소득(1인가구 100%)에 곱해 월소득 상한을 구한다 */
    @Column(name = "median_income_percent")
    private Integer medianIncomePercent;

    @Column(name = "require_basic_livelihood", nullable = false)
    private boolean requireBasicLivelihood;

    @Column(name = "require_sme", nullable = false)
    private boolean requireSme;

    @Column(name = "require_newlywed", nullable = false)
    private boolean requireNewlywed;

    @Column(name = "require_no_household", nullable = false)
    private boolean requireNoHousehold;

    /** 대출 상품 여부 - "정책 대출 활용"을 끈 사용자에게는 대출 상품을 추천하지 않는다 */
    @Column(name = "is_loan", nullable = false)
    private boolean loan;

    /** 관리자용 메모(CSV의 "특이사항" 열). 사용자 화면에는 나오지 않는다 */
    @Column(length = 300)
    private String note;

    /** 정책 안내/신청 홈페이지 주소 (http/https만, 없으면 null) */
    @Column(length = 500)
    private String link;

    public static HousingPolicy of(String region, String agency, String name, String description,
                                   Integer minAge, Integer maxAge, Integer maxAnnualIncome, Integer maxAsset,
                                   Integer medianIncomePercent, boolean requireBasicLivelihood, boolean requireSme,
                                   boolean requireNewlywed, boolean requireNoHousehold, boolean loan, String note, String link) {
        HousingPolicy p = new HousingPolicy();
        p.update(region, agency, name, description, minAge, maxAge, maxAnnualIncome, maxAsset, medianIncomePercent,
                requireBasicLivelihood, requireSme, requireNewlywed, requireNoHousehold, loan, note, link);
        return p;
    }

    public void update(String region, String agency, String name, String description,
                       Integer minAge, Integer maxAge, Integer maxAnnualIncome, Integer maxAsset,
                       Integer medianIncomePercent, boolean requireBasicLivelihood, boolean requireSme,
                       boolean requireNewlywed, boolean requireNoHousehold, boolean loan, String note, String link) {
        this.region = region;
        this.agency = agency;
        this.name = name;
        this.description = description;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.maxAnnualIncome = maxAnnualIncome;
        this.maxAsset = maxAsset;
        this.medianIncomePercent = medianIncomePercent;
        this.requireBasicLivelihood = requireBasicLivelihood;
        this.requireSme = requireSme;
        this.requireNewlywed = requireNewlywed;
        this.requireNoHousehold = requireNoHousehold;
        this.loan = loan;
        this.note = note;
        this.link = link;
    }
}
