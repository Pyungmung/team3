package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.entity.ListingFavorite;
import com.customhouse.domain.listing.repository.ListingFavoriteRepository;
import com.customhouse.domain.mypage.entity.HousingCondition;
import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.mail.MailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 관심매물 알림 서비스 테스트 - 가격이 바뀐 회원에게만 알림함 알림이 쌓이고 관심매물이 새 가격으로 갱신되는지,
 * 메일은 "알림 수신"을 켠 회원에게만 가는지, 메일이 실패해도 알림함 알림은 유지되는지 확인한다.
 */
class ListingAlertServiceTest {

    private static final String LISTING = "SEOCHO-202609-0001";

    private ListingFavoriteRepository favoriteRepository;
    private NotificationService notificationService;
    private HousingConditionRepository conditionRepository;
    private UserRepository userRepository;
    private MailClient mailClient;
    private ListingAlertService service;

    @BeforeEach
    void setUp() {
        favoriteRepository = mock(ListingFavoriteRepository.class);
        notificationService = mock(NotificationService.class);
        conditionRepository = mock(HousingConditionRepository.class);
        userRepository = mock(UserRepository.class);
        mailClient = mock(MailClient.class);
        service = new ListingAlertService(favoriteRepository, notificationService, conditionRepository, userRepository, mailClient);
    }

    private ListingFavorite favorite(long userId, int deposit, int rent) {
        return ListingFavorite.of(userId, LISTING, "서울 서초구 잠원로14길 42", "서초구", rent > 0 ? "월세" : "전세",
                "잠원 하이츠", "연립다세대", "301호", deposit, rent, 5, "{\"listing_deposit\":" + deposit + "}");
    }

    private void mailEnabled(long userId, boolean enabled) {
        HousingCondition condition = mock(HousingCondition.class);
        when(condition.isNotificationEnabled()).thenReturn(enabled);
        when(conditionRepository.findByUserId(userId)).thenReturn(Optional.of(condition));
        User user = new User();
        ReflectionTestUtils.setField(user, "email", "user" + userId + "@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void 보증금이_바뀌면_알림함에_알리고_관심매물을_새_가격으로_갱신한다() {
        ListingFavorite fav = favorite(1L, 5000, 80);
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(fav));
        mailEnabled(1L, false);

        int notified = service.notifyPriceChange(LISTING, 6000, 80);

        assertThat(notified).isEqualTo(1);
        verify(notificationService).notify(eq(1L), eq("관심 매물 가격 변동"), contains("보증금 5,000 → 6,000만원"));
        assertThat(fav.getDeposit()).isEqualTo(6000);
        assertThat(fav.getPreviousDeposit()).isEqualTo(5000);
        assertThat(fav.getPreviousMonthlyRent()).isEqualTo(80);
        assertThat(fav.getPriceChangedAt()).isNotNull();
        assertThat(fav.getSnapshot()).isNull();   // 옛 가격 기준 계산값이 남지 않게 카드 전체 정보는 비운다
        verify(favoriteRepository).save(fav);
    }

    @Test
    void 월세만_바뀌면_월세만_안내한다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80)));
        mailEnabled(1L, false);

        service.notifyPriceChange(LISTING, 5000, 90);

        verify(notificationService).notify(eq(1L), anyString(), argThat(c -> c.contains("월세 80 → 90만원") && !c.contains("보증금")));
    }

    @Test
    void 가격이_같으면_알림도_갱신도_하지_않는다() {
        ListingFavorite fav = favorite(1L, 5000, 80);
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(fav));

        assertThat(service.notifyPriceChange(LISTING, 5000, 80)).isZero();

        verify(notificationService, never()).notify(any(), any(), any());
        verify(favoriteRepository, never()).save(any());
        assertThat(fav.getSnapshot()).isNotNull();
    }

    @Test
    void 전세의_월세_null과_0은_같은_값으로_본다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 20000, 0)));

        assertThat(service.notifyPriceChange(LISTING, 20000, null)).isZero();
    }

    @Test
    void 이미_새_가격으로_갱신된_회원에게는_다시_알리지_않는다() {
        ListingFavorite fav = favorite(1L, 5000, 80);
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(fav));
        mailEnabled(1L, false);

        service.notifyPriceChange(LISTING, 6000, 80);
        service.notifyPriceChange(LISTING, 6000, 80);

        verify(notificationService, times(1)).notify(any(), any(), any());
    }

    @Test
    void 알림_수신을_켠_회원에게만_메일을_보낸다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80), favorite(2L, 5000, 80)));
        mailEnabled(1L, true);
        mailEnabled(2L, false);

        service.notifyPriceChange(LISTING, 6000, 80);

        verify(mailClient).send(eq("user1@example.com"), contains("가격이 변동"), contains("보증금 5,000 → 6,000만원"), isNull());
        verify(mailClient, never()).send(eq("user2@example.com"), any(), any(), any());
        verify(notificationService, times(2)).notify(any(), any(), any());   // 알림함은 둘 다
    }

    @Test
    void 메일_발송이_실패해도_알림함_알림과_가격_갱신은_유지된다() {
        ListingFavorite fav = favorite(1L, 5000, 80);
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(fav));
        mailEnabled(1L, true);
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED)).when(mailClient).send(any(), any(), any(), any());

        assertThat(service.notifyPriceChange(LISTING, 6000, 80)).isEqualTo(1);

        verify(notificationService).notify(eq(1L), anyString(), anyString());
        assertThat(fav.getDeposit()).isEqualTo(6000);
    }

    @Test
    void 허위매물_경고는_그_매물을_담은_모든_회원의_알림함에_쌓고_수신_켠_회원에게만_메일을_보낸다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80), favorite(2L, 5000, 80)));
        mailEnabled(1L, true);
        mailEnabled(2L, false);

        int count = service.notifyFlagged(LISTING, 2L);

        assertThat(count).isEqualTo(2);
        verify(notificationService).notify(eq(1L), eq("관심 매물 허위매물 경고"), contains("신고가 2건 누적"));
        verify(notificationService).notify(eq(2L), eq("관심 매물 허위매물 경고"), contains("신고가 2건 누적"));
        verify(mailClient).send(eq("user1@example.com"), contains("허위매물 주의"), anyString(), isNull());
        verify(mailClient, never()).send(eq("user2@example.com"), any(), any(), any());
    }

    @Test
    void 매물_삭제는_담은_회원의_알림함에_쌓고_수신_켠_회원에게만_메일을_보낸다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80), favorite(2L, 5000, 80)));
        mailEnabled(1L, true);
        mailEnabled(2L, false);

        int count = service.notifyDeleted(LISTING, 9L);

        assertThat(count).isEqualTo(2);
        verify(notificationService).notify(eq(1L), eq("관심 매물 삭제"), contains("삭제"));
        verify(notificationService).notify(eq(2L), eq("관심 매물 삭제"), contains("삭제"));
        verify(mailClient).send(eq("user1@example.com"), contains("삭제"), contains("잠원 하이츠"), isNull());
        verify(mailClient, never()).send(eq("user2@example.com"), any(), any(), any());
    }

    @Test
    void 매물을_삭제한_본인에게는_삭제_알림을_보내지_않는다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80), favorite(2L, 5000, 80)));
        mailEnabled(2L, false);

        assertThat(service.notifyDeleted(LISTING, 1L)).isEqualTo(1);

        verify(notificationService, never()).notify(eq(1L), any(), any());
        verify(notificationService).notify(eq(2L), eq("관심 매물 삭제"), anyString());
    }

    @Test
    void 삭제_알림_메일이_실패해도_알림함_알림은_유지된다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of(favorite(1L, 5000, 80)));
        mailEnabled(1L, true);
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED)).when(mailClient).send(any(), any(), any(), any());

        assertThat(service.notifyDeleted(LISTING, 9L)).isEqualTo(1);

        verify(notificationService).notify(eq(1L), eq("관심 매물 삭제"), anyString());
    }

    @Test
    void 담은_회원이_없으면_아무것도_하지_않는다() {
        when(favoriteRepository.findByListingId(LISTING)).thenReturn(List.of());

        assertThat(service.notifyPriceChange(LISTING, 1, 1)).isZero();
        assertThat(service.notifyFlagged(LISTING, 2L)).isZero();
        assertThat(service.notifyDeleted(LISTING, 9L)).isZero();
        verify(notificationService, never()).notify(any(), any(), any());
    }
}
