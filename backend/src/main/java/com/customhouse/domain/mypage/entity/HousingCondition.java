package com.customhouse.domain.mypage.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [담당: 황진구] 마이페이지 도메인 - 사용자 주거 조건 엔티티
 * 월급/직장 위치/희망 보증금·월세/알림 수신 여부를 저장·조회·수정하는 마이페이지 기능의 기반 스키마.
 * 사용자 1명당 1건(userId unique) - 진단 폼 재입력 없이 저장된 조건을 불러와 쓸 수 있게 한다.
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

    private Integer monthlyIncome;   // 월급 (만원)

    private String workLocation;     // 직장 위치

    private Integer desiredDeposit;  // 희망 보증금 (만원)

    private Integer desiredRent;     // 희망 월세 (만원)

    @Builder.Default
    @Column(nullable = false)
    private boolean notificationEnabled = false; // 알림 수신 여부 (담당: 김시연 - WatchList 알림과 연동 예정)
}
