package com.customhouse.domain.ad.service;

// [담당: 송귀성] 광고하기 서비스 - 주문 생성 검증(남의/삭제된 매물), 결제 승인(금액 변조 거절, 멱등, 매물 등록 + 광고 접수, 연장),
// 승인 후 실패 시 자동 결제 취소, 관리자 환불을 가짜 토스/AI 엔진으로 확인한다 (2026-10-08).

import com.customhouse.domain.ad.dto.AdConfirmResponse;
import com.customhouse.domain.ad.dto.AdOrderRequest;
import com.customhouse.domain.ad.dto.AdOrderResponse;
import com.customhouse.domain.ad.dto.MyAdResponse;
import com.customhouse.domain.ad.entity.AdOrder;
import com.customhouse.domain.ad.entity.ListingAd;
import com.customhouse.domain.ad.repository.AdOrderRepository;
import com.customhouse.domain.ad.repository.ListingAdRepository;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.service.AppSettingService;
import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.dto.ListingRegistrationResponse;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.domain.listing.service.ListingRegistrationService;
import com.customhouse.domain.payment.dto.PaymentConfirmRequest;
import com.customhouse.domain.payment.entity.Payment;
import com.customhouse.domain.payment.repository.PaymentRepository;
import com.customhouse.domain.payment.service.TossPaymentsClient;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdServiceTest {

    private static final Long USER = 7L;
    private static final String LISTING = "SEOCHO-202610-0001";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneId.of("Asia/Seoul")); // 2026-10-08 12:00 KST
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);

    private AdOrderRepository orders;
    private ListingAdRepository ads;
    private RegisteredListingRepository registered;
    private ListingRegistrationService registration;
    private PaymentRepository payments;
    private TossPaymentsClient toss;
    private ActiveAdService activeAds;
    private AdService service;

    /** 저장소를 흉내 내는 메모리 보관함 */
    private final List<AdOrder> savedOrders = new ArrayList<>();
    private final List<ListingAd> savedAds = new ArrayList<>();
    private final List<Payment> savedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orders = mock(AdOrderRepository.class);
        ads = mock(ListingAdRepository.class);
        registered = mock(RegisteredListingRepository.class);
        registration = mock(ListingRegistrationService.class);
        payments = mock(PaymentRepository.class);
        toss = mock(TossPaymentsClient.class);
        activeAds = mock(ActiveAdService.class);
        AppSettingService settings = mock(AppSettingService.class);
        when(settings.get()).thenReturn(new AppSettingResponse(500, 1990, 30, null));

        when(orders.save(any(AdOrder.class))).thenAnswer(inv -> remember(savedOrders, inv.getArgument(0)));
        when(orders.saveAndFlush(any(AdOrder.class))).thenAnswer(inv -> remember(savedOrders, inv.getArgument(0)));
        when(orders.findByOrderId(anyString())).thenAnswer(inv -> savedOrders.stream().filter(o -> o.getOrderId().equals(inv.getArgument(0))).findFirst());
        when(ads.saveAndFlush(any(ListingAd.class))).thenAnswer(inv -> remember(savedAds, inv.getArgument(0)));
        when(ads.save(any(ListingAd.class))).thenAnswer(inv -> remember(savedAds, inv.getArgument(0)));
        when(ads.findByListingId(anyString())).thenAnswer(inv -> savedAds.stream().filter(a -> a.getListingId().equals(inv.getArgument(0))).findFirst());
        when(payments.save(any(Payment.class))).thenAnswer(inv -> remember(savedPayments, inv.getArgument(0)));
        when(payments.findByOrderId(anyString())).thenAnswer(inv -> savedPayments.stream().filter(p -> p.getOrderId().equals(inv.getArgument(0))).findFirst());
        when(registered.findByListingId(LISTING)).thenReturn(Optional.of(RegisteredListing.of(LISTING, USER, "서초구", null)));
        when(registration.getDetail(LISTING)).thenReturn(Map.of("listing_status", "가능"));

        service = new AdService(orders, ads, registered, registration, payments, toss, settings, mock(UserRepository.class), activeAds, "test_ck_x", CLOCK);
    }

    private static <T> T remember(List<T> store, T item) {
        if (!store.contains(item)) {
            store.add(item);
        }
        return item;
    }

    private AdOrderResponse orderForExisting() {
        return service.createOrder(USER, new AdOrderRequest(LISTING, null));
    }

    private PaymentConfirmRequest confirmRequest(AdOrderResponse order) {
        return new PaymentConfirmRequest("pay_key_1", order.orderId(), order.amount());
    }

    // ---------- 주문 ----------

    @Test
    void 주문은_관리자_설정_가격과_기간으로_서버가_정한다() {
        AdOrderResponse order = orderForExisting();

        assertThat(order.amount()).isEqualTo(1990);
        assertThat(order.periodDays()).isEqualTo(30);
        assertThat(order.clientKey()).isEqualTo("test_ck_x");
        assertThat(order.orderId()).startsWith("AD_").hasSizeLessThanOrEqualTo(64);
        assertThat(savedOrders).singleElement().satisfies(o -> {
            assertThat(o.getStatus()).isEqualTo(AdOrder.Status.PENDING);
            assertThat(o.getListingId()).isEqualTo(LISTING);
        });
    }

    @Test
    void 남의_매물이나_삭제된_매물은_광고_주문을_만들_수_없다() {
        when(registered.findByListingId("OTHER-202610-0001")).thenReturn(Optional.of(RegisteredListing.of("OTHER-202610-0001", 99L, "서초구", null)));
        assertThatThrownBy(() -> service.createOrder(USER, new AdOrderRequest("OTHER-202610-0001", null)))
                .isInstanceOf(CustomException.class).extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.createOrder(USER, new AdOrderRequest("NOBODY-202610-0001", null)))
                .isInstanceOf(CustomException.class);

        when(registration.getDetail(LISTING)).thenReturn(Map.of("listing_status", "삭제됨"));
        assertThatThrownBy(this::orderForExisting).isInstanceOf(CustomException.class).hasMessageContaining("삭제된 매물");
        assertThat(savedOrders).isEmpty();
    }

    // ---------- 승인 ----------

    @Test
    void 결제_승인되면_광고가_접수되고_주문이_끝난다() {
        AdOrderResponse order = orderForExisting();

        AdConfirmResponse result = service.confirm(USER, confirmRequest(order));

        verify(toss).confirmPayment("pay_key_1", order.orderId(), 1990L);
        assertThat(result.listingId()).isEqualTo(LISTING);
        assertThat(result.registered()).isFalse();
        assertThat(result.expiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(savedAds).singleElement().satisfies(a -> assertThat(a.getExpiresAt()).isEqualTo(NOW.plusDays(30)));
        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.DONE);
        assertThat(savedPayments).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo(Payment.PaymentStatus.PAID);
            assertThat(p.getAmount()).isEqualTo(1990L);
        });
        verify(activeAds).evict();
    }

    @Test
    void 프론트가_보낸_금액이_주문_금액과_다르면_토스를_부르지_않고_거절한다() {
        AdOrderResponse order = orderForExisting();

        assertThatThrownBy(() -> service.confirm(USER, new PaymentConfirmRequest("pay_key_1", order.orderId(), 1L)))
                .isInstanceOf(CustomException.class).hasMessageContaining("금액");

        verify(toss, never()).confirmPayment(anyString(), anyString(), any());
        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.PENDING);
    }

    @Test
    void 남의_주문이나_없는_주문은_승인할_수_없다() {
        AdOrderResponse order = orderForExisting();

        assertThatThrownBy(() -> service.confirm(99L, confirmRequest(order))).isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.confirm(USER, new PaymentConfirmRequest("k", "AD_unknown", 1990L)))
                .isInstanceOf(CustomException.class).extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        verify(toss, never()).confirmPayment(anyString(), anyString(), any());
    }

    @Test
    void 같은_주문을_다시_승인해도_결제는_한_번만이고_같은_결과를_돌려준다() {
        AdOrderResponse order = orderForExisting();
        AdConfirmResponse first = service.confirm(USER, confirmRequest(order));

        AdConfirmResponse second = service.confirm(USER, confirmRequest(order));

        verify(toss, org.mockito.Mockito.times(1)).confirmPayment(anyString(), anyString(), any());
        assertThat(second.listingId()).isEqualTo(first.listingId());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(savedAds).hasSize(1);
    }

    @Test
    void 이미_광고_중인_매물을_다시_결제하면_만료일에_이어서_연장된다() {
        savedAds.add(ListingAd.start(LISTING, USER, NOW.minusDays(10), 30, "AD_old")); // 20일 남음
        AdOrderResponse order = orderForExisting();

        AdConfirmResponse result = service.confirm(USER, confirmRequest(order));

        assertThat(result.expiresAt()).isEqualTo(NOW.plusDays(20).plusDays(30));
        assertThat(savedAds).hasSize(1);
    }

    @Test
    void 광고가_끝난_매물을_다시_결제하면_지금부터_새로_시작한다() {
        savedAds.add(ListingAd.start(LISTING, USER, NOW.minusDays(45), 30, "AD_old")); // 15일 전에 끝남
        AdOrderResponse order = orderForExisting();

        AdConfirmResponse result = service.confirm(USER, confirmRequest(order));

        assertThat(result.expiresAt()).isEqualTo(NOW.plusDays(30));
    }

    @Test
    void 토스_승인이_실패하면_주문은_실패로_남고_광고는_생기지_않는다() {
        AdOrderResponse order = orderForExisting();
        doThrow(new CustomException(ErrorCode.PAYMENT_FAILED, "카드 한도 초과")).when(toss).confirmPayment(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.confirm(USER, confirmRequest(order))).hasMessageContaining("카드 한도 초과");

        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.FAILED);
        assertThat(savedAds).isEmpty();
        assertThat(savedPayments).isEmpty();
        verify(toss, never()).cancelPayment(anyString(), anyString());
    }

    // ---------- 매물 등록 + 광고 ----------

    private ListingRegistrationRequest newListing() {
        return new ListingRegistrationRequest("서울 서초구 동작대로 132", "오피스텔", "월세", 1000, 50, 23.5, "건물", "101호", "3", 2, 1,
                2015, 5, "수도", "가능", true, "2026-11-01", "설명", true, null, null, null, null, null, null, null);
    }

    @Test
    void 매물_등록과_광고하기_주문은_결제_승인_뒤에_매물을_등록하고_광고를_접수한다() {
        AdOrderResponse order = service.createOrder(USER, new AdOrderRequest(null, newListing()));
        assertThat(savedOrders.get(0).getListingId()).isNull();
        assertThat(savedOrders.get(0).getPendingListingJson()).contains("서울 서초구 동작대로 132");
        when(registration.register(eq(USER), any(ListingRegistrationRequest.class))).thenReturn(new ListingRegistrationResponse("SEOCHO-202610-0777", "서초구"));

        AdConfirmResponse result = service.confirm(USER, confirmRequest(order));

        ArgumentCaptor<ListingRegistrationRequest> registeredForm = ArgumentCaptor.forClass(ListingRegistrationRequest.class);
        verify(registration).register(eq(USER), registeredForm.capture());
        assertThat(registeredForm.getValue().addressKeyword()).isEqualTo("서울 서초구 동작대로 132");   // 저장해 둔 폼 내용이 그대로 등록된다
        assertThat(result.registered()).isTrue();
        assertThat(result.listingId()).isEqualTo("SEOCHO-202610-0777");
        assertThat(savedAds).singleElement().satisfies(a -> assertThat(a.getListingId()).isEqualTo("SEOCHO-202610-0777"));
        assertThat(savedOrders.get(0).getPendingListingJson()).isNull();   // 끝나면 임시 저장한 폼 내용은 비운다
    }

    @Test
    void 결제는_됐는데_매물_등록이_실패하면_결제를_자동으로_취소한다() {
        AdOrderResponse order = service.createOrder(USER, new AdOrderRequest(null, newListing()));
        when(registration.register(eq(USER), any(ListingRegistrationRequest.class))).thenThrow(new CustomException(ErrorCode.AI_ENGINE_ERROR));

        assertThatThrownBy(() -> service.confirm(USER, confirmRequest(order)))
                .isInstanceOf(CustomException.class).hasMessageContaining("결제를 취소했어요");

        verify(toss).cancelPayment(eq("pay_key_1"), anyString());
        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.FAILED);
        assertThat(savedPayments).singleElement().satisfies(p -> assertThat(p.getStatus()).isEqualTo(Payment.PaymentStatus.CANCELED));
        assertThat(savedAds).isEmpty();
    }

    @Test
    void 자동_취소마저_실패하면_관리자가_알_수_있게_주문에_남긴다() {
        AdOrderResponse order = service.createOrder(USER, new AdOrderRequest(null, newListing()));
        when(registration.register(eq(USER), any(ListingRegistrationRequest.class))).thenThrow(new CustomException(ErrorCode.AI_ENGINE_ERROR));
        doThrow(new CustomException(ErrorCode.PAYMENT_FAILED, "토스 장애")).when(toss).cancelPayment(anyString(), anyString());

        assertThatThrownBy(() -> service.confirm(USER, confirmRequest(order))).isInstanceOf(CustomException.class);

        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.FAILED);
        assertThat(savedOrders.get(0).getFailReason()).contains("수동으로 취소");
    }

    // ---------- 내 광고 / 환불 ----------

    @Test
    void 내_광고_현황은_광고중_여부와_남은_일수를_알려준다() {
        ListingAd active = ListingAd.start("A-1", USER, NOW.minusDays(10), 30, "o1");   // 20일 남음
        ListingAd ended = ListingAd.start("B-1", USER, NOW.minusDays(40), 30, "o2");
        when(ads.findByUserId(USER)).thenReturn(List.of(active, ended));

        List<MyAdResponse> mine = service.myAds(USER);

        assertThat(mine.get(0).active()).isTrue();
        assertThat(mine.get(0).remainingDays()).isEqualTo(20);
        assertThat(mine.get(1).active()).isFalse();
        assertThat(mine.get(1).remainingDays()).isZero();
    }

    @Test
    void 관리자_환불은_토스_결제를_취소하고_그_주문이_늘린_기간만큼_광고를_줄인다() {
        AdOrderResponse order = orderForExisting();
        service.confirm(USER, confirmRequest(order));
        // 같은 매물을 한 번 더 결제해 60일이 된 상태에서 첫 주문만 환불
        savedAds.get(0).extend(NOW, 30, "AD_second");

        service.cancel(order.orderId());

        verify(toss).cancelPayment(eq("pay_key_1"), anyString());
        assertThat(savedOrders.get(0).getStatus()).isEqualTo(AdOrder.Status.CANCELED);
        assertThat(savedAds.get(0).getExpiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(savedPayments.get(0).getStatus()).isEqualTo(Payment.PaymentStatus.CANCELED);
    }

    @Test
    void 환불은_광고가_접수된_주문만_할_수_있다() {
        AdOrderResponse order = orderForExisting();   // 아직 결제 전

        assertThatThrownBy(() -> service.cancel(order.orderId())).isInstanceOf(CustomException.class);
        verify(toss, never()).cancelPayment(anyString(), anyString());
    }
}
