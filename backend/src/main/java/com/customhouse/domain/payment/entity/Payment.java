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

/**
 * [담당: 황진구] 결제 도메인 - 토스페이먼츠 단건 결제(안심 매물 리포트) 엔티티
 * paymentKey/orderId/amount로 결제를 승인 처리하고 성공 시 PAID 상태로 저장한다.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(length = 100)
    private String orderName;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    public enum PaymentStatus {
        PENDING, PAID, CANCELED, FAILED
    }
}
