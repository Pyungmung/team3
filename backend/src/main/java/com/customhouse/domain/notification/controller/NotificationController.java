package com.customhouse.domain.notification.controller;

import com.customhouse.domain.notification.dto.NotificationResponse;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * [담당: 김시연] 알림 도메인 - 알림 목록 조회/읽음 처리 API
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(principal.id())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.countUnread(principal.id()))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
