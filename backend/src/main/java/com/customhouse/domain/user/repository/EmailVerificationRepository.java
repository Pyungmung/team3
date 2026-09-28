package com.customhouse.domain.user.repository;

import com.customhouse.domain.user.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 허겸] 회원 도메인 - EmailVerification JPA Repository
 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailVerification.Purpose purpose);
}
