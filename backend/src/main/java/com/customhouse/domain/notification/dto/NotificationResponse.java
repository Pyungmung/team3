package com.customhouse.domain.notification.dto;

import com.customhouse.domain.notification.entity.Notification;

import java.time.LocalDateTime;

/**
 * [담당: 김시연] 알림 도메인 - 알림 응답 DTO
 */
public record NotificationResponse(
        Long id,
        String title,
        String content,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
