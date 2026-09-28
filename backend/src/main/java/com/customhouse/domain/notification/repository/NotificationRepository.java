package com.customhouse.domain.notification.repository;

import com.customhouse.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [담당: 김시연] 알림 도메인 - Notification JPA Repository
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);

    /** 회원 탈퇴 시 알림함을 함께 정리한다 (domain.user.service.UserService 참고). */
    void deleteByUserId(Long userId);
}
