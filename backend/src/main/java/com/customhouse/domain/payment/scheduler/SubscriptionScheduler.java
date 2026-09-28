package com.customhouse.domain.payment.scheduler;

import com.customhouse.domain.payment.entity.Subscription;
import com.customhouse.domain.payment.service.SubscriptionService;
import com.customhouse.global.error.CustomException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [담당: 황진구] 결제 도메인 - 자동결제 스케줄러
 * 매일 오전 9시, ACTIVE 상태 구독 중 결제 예정일(nextPaymentDate)이 도래한 건을 자동으로 청구한다.
 * (global/config/SchedulingConfig의 @EnableScheduling이 있어야 동작한다.)
 */
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 0 9 * * *")
    public void chargeDueSubscriptions() {
        List<Subscription> dueSubscriptions = subscriptionService.findDueSubscriptions();
        log.info("[구독 자동결제] 대상 {}건 처리 시작", dueSubscriptions.size());

        for (Subscription subscription : dueSubscriptions) {
            try {
                subscriptionService.charge(subscription);
            } catch (CustomException e) {
                // 한 건 실패가 나머지 배치를 막지 않도록 여기서 흡수하고 다음 건 진행. (로깅은 SubscriptionService에서 이미 처리)
            }
        }
    }
}
