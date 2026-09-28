package com.customhouse.domain.notification.entity;

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
 * [담당: 김시연] 알림 도메인 - notifications 테이블 엔티티
 * WatchList 매물 변동 사항 및 추천 이슈 발생 시 사용자에게 전달되는 알림 이력.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    // MySQL 예약어라 컬럼명을 is_read로 매핑한다 (JPA 필드명 read는 그대로 유지, DB 컬럼명만 다름).
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
