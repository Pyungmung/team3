package com.customhouse.domain.payment.repository;

import com.customhouse.domain.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * [담당: 황진구] 결제 도메인 - Subscription JPA Repository
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    /** SubscriptionScheduler가 매일 오전 9시 자동결제 대상을 조회할 때 사용. */
    List<Subscription> findByStatusAndNextPaymentDateLessThanEqual(
            Subscription.SubscriptionStatus status, LocalDate date);
}
