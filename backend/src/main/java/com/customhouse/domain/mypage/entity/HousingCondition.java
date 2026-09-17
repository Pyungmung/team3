package com.customhouse.domain.mypage.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * [담당: 황진구] 마이페이지 도메인 - 사용자 주거 조건 엔티티
 * 직장 위치/희망 보증금·월세/알림 수신 여부에 더해, 청년 주거지원 정책 매칭에 쓰이는
 * 프로필(나이/소득/자산/직업종류/무주택여부/우대사항)까지 저장한다.
 * 사용자 1명당 1건(userId unique) - 진단 폼 재입력 없이 저장된 조건을 불러와 쓸 수 있게 한다.
 *
 * 2026-09-16: "월급(월 단위)" 필드를 없애고 "연소득"으로 통합했다 - AI 엔진
 * (customhouse-ai/app/models/request_schema.py)이 예산 계산과 정책 소득기준 판별 모두
 * effective_monthly_income(= max(연소득, 부부합산 연소득) / 12) 하나로 통일해서 쓴다.
 */
@Entity
@Table(name = "housing_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HousingCondition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private Integer age; // 나이(만)

    @Column(nullable = false)
    private Integer annualIncome; // 소득 (연소득, 만원)

    private Integer coupleAnnualIncome; // 부부합산 연소득 (만원, 선택) - 계산 시 annualIncome과 비교해 더 큰 값을 씀

    private String workLocation;     // 직장 위치

    private Integer desiredDeposit;  // 희망 보증금 (만원)

    private Integer desiredRent;     // 희망 월세 (만원)

    // --- 자산 구성 (만원). 총자산액 = (부동산+자동차+금융자산+일반자산) - (금융부채+일반부채).
    // 값 어긋남을 막기 위해 합계를 별도 컬럼으로 저장하지 않고 getNetAsset()으로 매번 계산한다. ---
    @Builder.Default
    private Integer realEstateAsset = 0; // 부동산

    @Builder.Default
    private Integer carAsset = 0; // 자동차

    @Builder.Default
    private Integer financialAsset = 0; // 금융자산

    @Builder.Default
    private Integer otherAsset = 0; // 일반자산

    @Builder.Default
    private Integer financialDebt = 0; // 금융부채

    @Builder.Default
    private Integer otherDebt = 0; // 일반부채

    @Enumerated(EnumType.STRING)
    private JobType jobType; // 직업종류 (공무원/중소기업/중견기업/대기업)

    private Boolean noHouseholder; // 무주택여부

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "housing_condition_preferences", joinColumns = @JoinColumn(name = "housing_condition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "preferential_status")
    private Set<PreferentialStatus> preferentialStatuses = new HashSet<>(); // 우대사항 (다중 선택)

    @Builder.Default
    @Column(nullable = false)
    private boolean notificationEnabled = false; // 알림 수신 여부 (담당: 김시연 - WatchList 알림과 연동 예정)

    /** 총자산액 (만원). null인 구성요소는 0으로 취급한다. */
    public int getNetAsset() {
        int assets = nz(realEstateAsset) + nz(carAsset) + nz(financialAsset) + nz(otherAsset);
        int debts = nz(financialDebt) + nz(otherDebt);
        return assets - debts;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
