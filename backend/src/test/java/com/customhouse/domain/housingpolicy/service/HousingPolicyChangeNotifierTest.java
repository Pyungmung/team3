package com.customhouse.domain.housingpolicy.service;

// [담당: 송귀성] 주거정책 수정/삭제 알림 - 담은 회원에게 문구가 가고 알림함과 같은 내용으로 메일도 시도하고, 한 명에게 실패해도 나머지에게는 계속 보내고, 긴 내용은 500자로 자른다 (2026-10-08).

import com.customhouse.domain.housingpolicy.entity.HousingPolicy;
import com.customhouse.domain.notification.service.NotificationMailSender;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.domain.policy.entity.FavoritePolicy;
import com.customhouse.domain.policy.repository.FavoritePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HousingPolicyChangeNotifierTest {

    private FavoritePolicyRepository favorites;
    private NotificationService notifications;
    private NotificationMailSender mailSender;
    private HousingPolicyChangeNotifier notifier;
    private HousingPolicy policy;

    @BeforeEach
    void setUp() {
        favorites = mock(FavoritePolicyRepository.class);
        notifications = mock(NotificationService.class);
        mailSender = mock(NotificationMailSender.class);
        notifier = new HousingPolicyChangeNotifier(favorites, notifications, mailSender);
        policy = HousingPolicy.of("강남구", "서울시", "청년월세지원", "월 20만원", null, null, null, null, null,
                false, false, false, false, false, null, null);
    }

    @Test
    void 이_정책을_담은_회원_목록을_돌려준다() {
        when(favorites.findByPolicyId(5L)).thenReturn(List.of(FavoritePolicy.of(11L, 5L), FavoritePolicy.of(12L, 5L)));

        assertThat(notifier.watchers(5L)).containsExactly(11L, 12L);
    }

    @Test
    void 수정_알림은_정책_이름과_바뀐_항목을_알려준다() {
        int sent = notifier.policyChanged(policy, List.of("지원혜택", "링크"), List.of(11L, 12L));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(eq(11L), eq("관심 정책 변경"), content.capture());
        verify(notifications).notify(eq(12L), eq("관심 정책 변경"), any());
        assertThat(sent).isEqualTo(2);
        assertThat(content.getValue()).contains("서울시 · 청년월세지원").contains("지원혜택, 링크").contains("수정되었어요");
    }

    @Test
    void 삭제_알림은_종료되어_관심정책에서_빠졌다고_알려준다() {
        notifier.policyDeleted(policy, List.of(11L));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(eq(11L), eq("관심 정책 삭제"), content.capture());
        assertThat(content.getValue()).contains("서울시 · 청년월세지원").contains("관심정책에서 빠졌어요");
    }

    @Test
    void 한_명에게_알림이_실패해도_나머지에게는_보낸다() {
        doThrow(new RuntimeException("boom")).when(notifications).notify(eq(11L), any(), any());

        int sent = notifier.policyChanged(policy, List.of("링크"), List.of(11L, 12L));

        verify(notifications).notify(eq(12L), any(), any());
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void 알림_내용은_500자를_넘지_않는다() {
        HousingPolicy longName = HousingPolicy.of("강남구", "기관", "가".repeat(150), "x", null, null, null, null, null,
                false, false, false, false, false, null, null);

        notifier.policyChanged(longName, List.of("나이 조건", "연소득 조건", "총자산 조건", "기준중위소득 조건", "기초수급자 조건", "중소기업 조건"), List.of(11L));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(eq(11L), any(), content.capture());
        assertThat(content.getValue().length()).isLessThanOrEqualTo(500);
    }

    @Test
    void 수정과_삭제_알림은_알림함과_함께_메일_발송도_시도한다() {
        notifier.policyChanged(policy, List.of("지원혜택", "링크"), List.of(11L));
        notifier.policyDeleted(policy, List.of(12L));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendIfEnabled(eq(11L), eq("[맞집] 관심 정책 내용이 변경됐어요"), body.capture());
        assertThat(body.getValue()).contains("서울시 · 청년월세지원").contains("지원혜택, 링크").contains("관심정책 조회");
        verify(mailSender).sendIfEnabled(eq(12L), eq("[맞집] 관심 정책이 삭제됐어요"), any());
    }

    @Test
    void 알림함_알림을_쌓지_못한_회원에게는_메일도_보내지_않는다() {
        doThrow(new RuntimeException("boom")).when(notifications).notify(eq(11L), any(), any());

        notifier.policyChanged(policy, List.of("링크"), List.of(11L, 12L));

        verify(mailSender, never()).sendIfEnabled(eq(11L), any(), any());
        verify(mailSender).sendIfEnabled(eq(12L), any(), any());
    }
}
