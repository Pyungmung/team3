package com.customhouse.domain.payment.repository;

import com.customhouse.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 황진구] 결제 도메인 - Payment JPA Repository
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    /** 회원 탈퇴 시 결제 이력을 함께 정리한다 (domain.user.service.UserService 참고). */
    void deleteByUserId(Long userId);
}
