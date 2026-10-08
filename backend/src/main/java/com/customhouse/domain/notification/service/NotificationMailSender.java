package com.customhouse.domain.notification.service;

import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.mail.MailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [담당: 송귀성] 알림함 알림과 함께 보내는 메일 (2026-10-08). 마이페이지 "알림 수신"(HousingCondition.notificationEnabled)을 켠 회원에게만 보낸다.
 * 메일 발송이 실패해도 알림함 알림과 원래 작업(정책 수정 등)은 그대로 유지하고 실패는 로그에만 남긴다 (SendGrid 장애로 저장이 막히면 안 된다).
 * 관심매물 알림(ListingAlertService)은 같은 규칙을 자체 메서드로 갖고 있다 - 새 알림(관심정책 변경/삭제)은 이 클래스를 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMailSender {

    private final HousingConditionRepository housingConditionRepository;
    private final UserRepository userRepository;
    private final MailClient mailClient;

    /** @return 메일을 실제로 보냈으면 true (알림 수신을 끈 회원/이메일 없음/발송 실패는 false) */
    public boolean sendIfEnabled(Long userId, String subject, String text) {
        try {
            boolean enabled = housingConditionRepository.findByUserId(userId)
                    .map(c -> c.isNotificationEnabled())
                    .orElse(false);
            if (!enabled) {
                return false;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return false;
            }
            mailClient.send(user.getEmail(), subject, text, null);
            return true;
        } catch (RuntimeException e) {
            log.warn("알림 메일 발송에 실패했습니다 (회원: {}): {}", userId, e.toString());
            return false;
        }
    }
}
