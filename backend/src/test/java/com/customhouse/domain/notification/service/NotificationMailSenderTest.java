package com.customhouse.domain.notification.service;

// [담당: 송귀성] 알림 메일 - "알림 수신"을 켠 회원에게만 보내고, 이메일이 없거나 발송이 실패해도 예외 없이 false를 돌려준다 (2026-10-08).

import com.customhouse.domain.mypage.entity.HousingCondition;
import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.mail.MailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMailSenderTest {

    private HousingConditionRepository conditions;
    private UserRepository users;
    private MailClient mailClient;
    private NotificationMailSender sender;

    @BeforeEach
    void setUp() {
        conditions = mock(HousingConditionRepository.class);
        users = mock(UserRepository.class);
        mailClient = mock(MailClient.class);
        sender = new NotificationMailSender(conditions, users, mailClient);
    }

    private void notificationEnabled(boolean enabled) {
        HousingCondition condition = mock(HousingCondition.class);
        when(condition.isNotificationEnabled()).thenReturn(enabled);
        when(conditions.findByUserId(7L)).thenReturn(Optional.of(condition));
    }

    private void userWithEmail(String email) {
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).email(email).nickname("n").build()));
    }

    @Test
    void 알림_수신을_켠_회원에게_메일을_보낸다() {
        notificationEnabled(true);
        userWithEmail("user@test.com");

        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isTrue();

        verify(mailClient).send(eq("user@test.com"), eq("제목"), eq("내용"), any());
    }

    @Test
    void 알림_수신을_껐거나_조건을_저장한_적_없으면_보내지_않는다() {
        notificationEnabled(false);
        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isFalse();

        when(conditions.findByUserId(7L)).thenReturn(Optional.empty());
        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isFalse();

        verify(mailClient, never()).send(any(), any(), any(), any());
    }

    @Test
    void 이메일이_없는_회원에게는_보내지_않는다() {
        notificationEnabled(true);
        userWithEmail(" ");
        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isFalse();

        when(users.findById(7L)).thenReturn(Optional.empty());
        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isFalse();

        verify(mailClient, never()).send(any(), any(), any(), any());
    }

    @Test
    void 발송이_실패해도_예외를_밖으로_내지_않는다() {
        notificationEnabled(true);
        userWithEmail("user@test.com");
        doThrow(new CustomException(ErrorCode.INTERNAL_ERROR)).when(mailClient).send(any(), any(), any(), any());

        assertThat(sender.sendIfEnabled(7L, "제목", "내용")).isFalse();
    }
}
