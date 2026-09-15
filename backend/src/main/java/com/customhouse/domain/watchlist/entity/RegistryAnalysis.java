package com.customhouse.domain.watchlist.entity;

import com.customhouse.global.common.BaseTimeEntity;

/**
 * [담당: 김시연] WatchList 도메인 - registry_analysis 테이블(등기부등본 위험도 분석 결과) 엔티티 설계
 * RegistryLog 변동 이력을 바탕으로 산출한 매물별 위험도 점수/요약을 저장한다.
 * TODO: JPA(@Entity) 및 MySQL 연동 시 활성화 예정 (현재는 MVP 단계로 스키마 설계만 반영)
 */
public class RegistryAnalysis extends BaseTimeEntity {

    private Long id;
    private Long propertyId;
    private Integer riskScore; // 0~100, 높을수록 위험
    private String summary;
}
