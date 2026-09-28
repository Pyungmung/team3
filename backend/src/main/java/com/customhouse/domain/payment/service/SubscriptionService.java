package com.customhouse.domain.payment.service;

import com.customhouse.domain.payment.dto.BillingKeyRequest;
import com.customhouse.domain.payment.dto.SubscriptionResponse;
import com.customhouse.domain.payment.entity.Subscription;
import com.customhouse.domain.payment.repository.SubscriptionRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * [담당: 황진구] 결제 도메인 - 맞집 프리미엄 월 정기 구독(자동결제)
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String SUBSCRIPTION_ORDER_NAME = "맞집 프리미엄 월 구독";
    public static final long SUBSCRIPTION_AMOUNT = 2_900L; // 원

    private final SubscriptionRepository subscriptionRepository;
    private final TossPaymentsClient tossPaymentsClient;

    /** requestBillingAuth() 성공 후 받은 authKey로 빌링키를 발급받아 저장(=자동결제 수단 등록)한다. */
    @Transactional
    public SubscriptionResponse issueBillingKey(Long userId, BillingKeyRequest request) {
        var tossResponse = tossPaymentsClient.issueBillingKey(request.customerKey(), request.authKey());
        String billingKey = (String) tossResponse.get("billingKey");

        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> Subscription.builder().userId(userId).build());

        subscription.setCustomerKey(request.customerKey());
        subscription.setBillingKey(billingKey);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setNextPaymentDate(LocalDate.now()); // 등록 직후 바로 첫 결제 대상이 되도록(테스트 편의)

        subscriptionRepository.save(subscription);
        return SubscriptionResponse.from(subscription);
    }

    public SubscriptionResponse getMySubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        return SubscriptionResponse.from(subscription);
    }

    /** 등록된 빌링키로 즉시 1회 청구한다. 정식 플로우는 SubscriptionScheduler가 매일 자동 실행한다. */
    @Transactional
    public SubscriptionResponse chargeNow(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "활성화된 구독이 없습니다. 먼저 자동결제 수단을 등록해주세요."));

        charge(subscription);
        return SubscriptionResponse.from(subscription);
    }

    /** [담당: 황진구] SubscriptionScheduler가 호출하는 실제 청구 로직. */
    @Transactional
    public void charge(Subscription subscription) {
        String orderId = "SUB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            tossPaymentsClient.chargeBilling(
                    subscription.getBillingKey(),
                    subscription.getCustomerKey(),
                    SUBSCRIPTION_AMOUNT,
                    orderId,
                    SUBSCRIPTION_ORDER_NAME
            );
            subscription.setNextPaymentDate(LocalDate.now().plusMonths(1));
            subscriptionRepository.save(subscription);
        } catch (CustomException e) {
            // 결제 실패 시 nextPaymentDate를 그대로 둬서 스케줄러가 다음 날 다시 시도하게 한다.
            log.warn("구독 자동결제 실패 (userId={}, orderId={}): {}", subscription.getUserId(), orderId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void cancel(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELED);
        subscriptionRepository.save(subscription);
    }

    /** [담당: 황진구] SubscriptionScheduler 전용: 오늘 결제해야 하는 ACTIVE 구독 목록. */
    public List<Subscription> findDueSubscriptions() {
        return subscriptionRepository.findByStatusAndNextPaymentDateLessThanEqual(
                Subscription.SubscriptionStatus.ACTIVE, LocalDate.now());
    }
}
