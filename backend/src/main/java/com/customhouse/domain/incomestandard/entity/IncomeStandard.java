package com.customhouse.domain.incomestandard.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 기준소득 통계 (관리자 수정 > 기준소득관리). 앱 전체가 공유하는 값이라 행이 하나뿐인 싱글톤 테이블이다(id=1 고정).
 * RIR(소득 대비 주택임대료 비율, %)은 국토교통부 「주거실태조사」, 기준중위소득은 보건복지부 고시를 관리자가 직접 입력해 관리한다.
 * 이전에는 docs/RIR.csv + docs/housing_policy_list.csv(15열)를 AI 엔진이 파일로 직접 읽었는데, 이제 이 테이블 값을
 * 진단 요청마다 AI 엔진에 실어 보내는 방식으로 바뀐다(파일은 이 값이 비어 있을 때만 쓰는 폴백으로 남는다).
 */
@Entity
@Table(name = "income_standards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncomeStandard extends BaseTimeEntity {

    /** 싱글톤이라 항상 1L 고정 (자동 생성 안 함). */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** RIR(%): 전국(전체) - 리포트에는 참고용으로만 표시되고, 최상위 기준은 수도권이다. */
    private Double rirOverallPercent;

    /** RIR(%): 수도권 - 리포트의 "적정 월세 상한" 계산과 헤드라인 표시에 쓰는 최상위 기준값. */
    private Double rirMetroPercent;

    /** RIR(%): 소득수준별 하위(1-4분위) / 중위(5-8분위) / 상위(9-10분위). */
    private Double rirLowPercent;
    private Double rirMidPercent;
    private Double rirHighPercent;

    /** 위 RIR 값들의 기준연도 (예: 2024) - 리포트 캡션/출처 표시용. */
    private Integer rirYear;

    /** 위 RIR 값들의 출처 (예: "국토교통부,「주거실태조사」") - 리포트 출처 표시용. */
    private String rirSource;

    /** 기준중위소득 100%(1인가구, 월, 원 단위) - 보건복지부 고시 원문 그대로 1원 단위까지 정확히 관리한다.
     * 정책 소득 상한 계산(정책별 % x 이 값)에서만 만원 단위로 환산해 쓴다. */
    private Long medianIncome100PercentMonthly;

    public static IncomeStandard singleton() {
        IncomeStandard entity = new IncomeStandard();
        entity.id = SINGLETON_ID;
        return entity;
    }

    public void update(Double rirOverallPercent, Double rirMetroPercent, Double rirLowPercent, Double rirMidPercent,
                        Double rirHighPercent, Integer rirYear, String rirSource, Long medianIncome100PercentMonthly) {
        this.rirOverallPercent = rirOverallPercent;
        this.rirMetroPercent = rirMetroPercent;
        this.rirLowPercent = rirLowPercent;
        this.rirMidPercent = rirMidPercent;
        this.rirHighPercent = rirHighPercent;
        this.rirYear = rirYear;
        this.rirSource = rirSource;
        this.medianIncome100PercentMonthly = medianIncome100PercentMonthly;
    }
}
