package com.customhouse.domain.watchlist.entity;

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

import java.time.LocalDateTime;

/**
 * [담당: 김시연] WatchList 도메인 - registry_logs 테이블(매물 변동 이력) 엔티티
 * 전세사기 예방을 위한 실거래가/등기부등본 변동 감지 이력을 적재한다.
 * MVP 단계에서는 실제 등기부등본 API 대신, 매물 시세(보증금/월세) 변동을 감지해 기록한다
 * (실거래가 API 연동은 domain/recommendation·customhouse-ai의 api_collector.py TODO와 함께 추후 확장).
 */
@Entity
@Table(name = "registry_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistryLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(length = 50)
    private String changeType; // 예: "보증금 변경", "월세 변경"

    @Column(length = 500)
    private String description;

    private LocalDateTime detectedAt;
}
