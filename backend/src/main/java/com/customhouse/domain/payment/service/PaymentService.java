package com.customhouse.domain.payment.service;

import com.customhouse.domain.payment.dto.PaymentConfirmRequest;
import com.customhouse.domain.payment.dto.PaymentResponse;
import com.customhouse.domain.payment.entity.Payment;
import com.customhouse.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * [담당: 황진구] 결제 도메인 - 안심 매물 리포트 단건 결제
 * 토스 결제창(SDK) → successUrl 리다이렉트 → 이 서비스가 서버 사이드에서 결제를 최종 승인한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String DEFAULT_ORDER_NAME = "맞집 안심 매물 리포트";

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;

    @Transactional
    public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
        // 결제 요청 시점에 만든 PENDING 레코드가 있으면 재사용하고, 없으면(=사전 주문 생성 없이 바로 결제한 경우) 새로 만든다.
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElseGet(() -> Payment.builder()
                        .userId(userId)
                        .orderId(request.orderId())
                        .amount(request.amount())
                        .orderName(DEFAULT_ORDER_NAME)
                        .build());

        Map<String, Object> tossResponse = tossPaymentsClient.confirmPayment(
                request.paymentKey(), request.orderId(), request.amount());

        payment.setPaymentKey(request.paymentKey());
        payment.setAmount(request.amount());
        payment.setOrderName((String) tossResponse.getOrDefault("orderName", DEFAULT_ORDER_NAME));
        payment.setStatus(Payment.PaymentStatus.PAID);

        paymentRepository.save(payment);
        return PaymentResponse.from(payment);
    }
}
