package com.customhouse.domain.notification.service;

import com.customhouse.domain.notification.dto.NotificationResponse;
import com.customhouse.domain.notification.entity.Notification;
import com.customhouse.domain.notification.repository.NotificationRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [담당: 김시연] 알림 도메인 - 매물 상태 변화 발생 시 알림 제공을 통한 사용자 대처 흐름 구현
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** WatchlistService 등 다른 서비스가 알림을 발행할 때 사용하는 내부 API. */
    @Transactional
    public void notify(Long userId, String title, String content) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .build());
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
