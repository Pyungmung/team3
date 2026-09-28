package com.customhouse.domain.payment.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * [담당: 황진구] 결제 도메인 - 월 정기 구독(빌링) 엔티티
 * BillingKey/customerKey를 저장하고, payment/scheduler(SubscriptionScheduler)가
 * 매일 오전 9시 ACTIVE 상태 구독을 조회해 결제 예정일이 지난 건을 자동 결제한다.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(length = 200)
    private String billingKey;

    @Column(nullable = false, unique = true, length = 100)
    private String customerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private LocalDate nextPaymentDate;

    private LocalDate expiredAt;

    public enum SubscriptionStatus {
        ACTIVE, CANCELED
    }
}
