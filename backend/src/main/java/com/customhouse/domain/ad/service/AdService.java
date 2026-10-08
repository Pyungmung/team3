package com.customhouse.domain.ad.service;

import com.customhouse.domain.ad.dto.AdAdminOrderMapper;
import com.customhouse.domain.ad.dto.AdConfigResponse;
import com.customhouse.domain.ad.dto.AdConfirmResponse;
import com.customhouse.domain.ad.dto.AdOrderRequest;
import com.customhouse.domain.ad.dto.AdOrderResponse;
import com.customhouse.domain.ad.dto.AdminAdOrderResponse;
import com.customhouse.domain.ad.dto.AdminListingAdResponse;
import com.customhouse.domain.ad.dto.MyAdResponse;
import com.customhouse.domain.ad.entity.AdOrder;
import com.customhouse.domain.ad.entity.ListingAd;
import com.customhouse.domain.ad.repository.AdOrderRepository;
import com.customhouse.domain.ad.repository.ListingAdRepository;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.service.AppSettingService;
import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.domain.listing.service.ListingRegistrationService;
import com.customhouse.domain.payment.dto.PaymentConfirmRequest;
import com.customhouse.domain.payment.entity.Payment;
import com.customhouse.domain.payment.repository.PaymentRepository;
import com.customhouse.domain.payment.service.TossPaymentsClient;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [담당: 송귀성] 광고하기 (2026-10-08) - 토스페이먼츠 단건 결제(기본 1,990원)로 매물을 광고 매물로 접수한다.
 *
 * 흐름: createOrder(주문 생성, 금액/기간은 서버가 정함) -> 프론트가 토스 결제창을 열고 결제 -> confirm(승인 + 매물 등록 + 광고 접수).
 * 결제는 승인됐는데 매물 등록/광고 접수가 실패하면 결제를 자동으로 취소한다(돈만 나가고 남는 일이 없게). 같은 주문을 두 번 승인해도
 * 이미 끝난 주문이면 같은 결과를 돌려준다(새로고침 안전). 이 서비스 메서드는 AI 엔진/토스 HTTP 호출이 끼어 있어서 하나의 DB 트랜잭션으로 묶지 않고,
 * 저장소 호출마다 각자 커밋한다 - 단계 사이에서 실패해도 주문 상태(PROCESSING/FAILED)가 DB에 남아 관리자가 확인할 수 있다.
 */
@Slf4j
@Service
public class AdService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DELETED_STATUS = "삭제됨";

    private final AdOrderRepository orderRepository;
    private final ListingAdRepository adRepository;
    private final RegisteredListingRepository registeredListingRepository;
    private final ListingRegistrationService registrationService;
    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final AppSettingService appSettingService;
    private final UserRepository userRepository;
    private final ActiveAdService activeAdService;
    private final String clientKey;
    private final Clock clock;

    @Autowired
    public AdService(AdOrderRepository orderRepository, ListingAdRepository adRepository,
                     RegisteredListingRepository registeredListingRepository, ListingRegistrationService registrationService,
                     PaymentRepository paymentRepository, TossPaymentsClient tossPaymentsClient,
                     AppSettingService appSettingService, UserRepository userRepository, ActiveAdService activeAdService,
                     @Value("${toss.client-key:}") String clientKey) {
        this(orderRepository, adRepository, registeredListingRepository, registrationService, paymentRepository, tossPaymentsClient,
                appSettingService, userRepository, activeAdService, clientKey, Clock.systemDefaultZone());
    }

    /** 테스트용: 시계를 바꿔 끼울 수 있다. */
    AdService(AdOrderRepository orderRepository, ListingAdRepository adRepository,
              RegisteredListingRepository registeredListingRepository, ListingRegistrationService registrationService,
              PaymentRepository paymentRepository, TossPaymentsClient tossPaymentsClient,
              AppSettingService appSettingService, UserRepository userRepository, ActiveAdService activeAdService,
              String clientKey, Clock clock) {
        this.orderRepository = orderRepository;
        this.adRepository = adRepository;
        this.registeredListingRepository = registeredListingRepository;
        this.registrationService = registrationService;
        this.paymentRepository = paymentRepository;
        this.tossPaymentsClient = tossPaymentsClient;
        this.appSettingService = appSettingService;
        this.userRepository = userRepository;
        this.activeAdService = activeAdService;
        this.clientKey = clientKey == null ? "" : clientKey;
        this.clock = clock;
    }

    // ---------- 안내 / 주문 ----------

    public AdConfigResponse config() {
        AppSettingResponse settings = appSettingService.get();
        return new AdConfigResponse(settings.adPriceWon(), settings.adPeriodDays(), clientKey);
    }

    public AdOrderResponse createOrder(Long userId, AdOrderRequest request) {
        AppSettingResponse settings = appSettingService.get();
        int price = settings.adPriceWon();
        int period = settings.adPeriodDays();

        String listingId = null;
        String pendingJson = null;
        if (request.listing() != null) {
            pendingJson = JSON.writeValueAsString(request.listing());
        } else {
            listingId = request.listingId().trim();
            requireMyLiveListing(userId, listingId);
        }

        String orderId = "AD_" + UUID.randomUUID().toString().replace("-", "");
        orderRepository.save(AdOrder.of(orderId, userId, listingId, pendingJson, price, period));
        return new AdOrderResponse(orderId, "맞집 광고하기 (" + period + "일)", price, period, clientKey);
    }

    /** 내 매물이고 삭제되지 않았는지 확인한다 (남의 매물/삭제된 매물은 광고할 수 없다). */
    private void requireMyLiveListing(Long userId, String listingId) {
        RegisteredListing owner = registeredListingRepository.findByListingId(listingId).orElse(null);
        if (owner == null || !owner.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인이 등록한 매물만 광고할 수 있어요.");
        }
        Map<String, Object> detail = registrationService.getDetail(listingId); // 없으면 NOT_FOUND
        if (DELETED_STATUS.equals(detail.get("listing_status"))) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "삭제된 매물은 광고할 수 없어요.");
        }
    }

    // ---------- 결제 승인 ----------

    public AdConfirmResponse confirm(Long userId, PaymentConfirmRequest request) {
        AdOrder order = orderRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "광고 주문을 찾을 수 없어요."));
        if (!order.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인의 주문만 결제 승인할 수 있어요.");
        }
        if (order.getStatus() == AdOrder.Status.DONE) {
            return doneResponse(order, false); // 새로고침 등으로 다시 불린 경우 - 이미 끝난 결과를 그대로
        }
        if (order.getStatus() != AdOrder.Status.PENDING) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "이미 처리 중이거나 처리가 끝난(실패·환불) 주문이에요. 광고하기를 처음부터 다시 진행해주세요.");
        }
        if (!order.getAmount().equals(request.amount())) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "결제 금액이 주문 금액과 달라요.");
        }

        order.markProcessing(request.paymentKey());
        orderRepository.saveAndFlush(order); // 같은 주문을 동시에 두 번 승인하지 못하게 먼저 PROCESSING으로 바꿔 둔다

        try {
            tossPaymentsClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());
        } catch (CustomException e) {
            order.markFailed("토스 결제 승인 실패: " + e.getMessage());
            orderRepository.save(order);
            throw e;
        }
        Payment payment = paymentRepository.save(Payment.builder()
                .userId(userId)
                .orderId(order.getOrderId())
                .paymentKey(request.paymentKey())
                .orderName("맞집 광고하기 (" + order.getPeriodDays() + "일)")
                .amount(order.getAmount())
                .status(Payment.PaymentStatus.PAID)
                .build());

        boolean registered = false;
        try {
            String listingId = order.getListingId();
            if (listingId == null) {
                ListingRegistrationRequest listing = JSON.readValue(order.getPendingListingJson(), ListingRegistrationRequest.class);
                listingId = registrationService.register(userId, listing).listingId();
                registered = true;
                order.attachListing(listingId); // 이후 단계가 실패해도 어느 매물이 등록됐는지 남는다
            }
            activate(listingId, userId, order);
            order.markDone(listingId);
            orderRepository.save(order);
            activeAdService.evict();
        } catch (RuntimeException e) {
            refundAfterFailure(order, payment, e);
            String detail = registered
                    ? "매물은 등록됐지만 광고 접수에 실패해 결제를 취소했어요. 마이페이지에서 광고하기를 다시 눌러주세요."
                    : "매물 등록/광고 접수에 실패해 결제를 취소했어요. 잠시 후 다시 시도해주세요.";
            throw new CustomException(ErrorCode.PAYMENT_FAILED, detail);
        }
        return doneResponse(order, registered);
    }

    /** 결제는 됐는데 후속 처리가 실패했다 - 토스 결제를 취소하고 주문을 FAILED로 남긴다. 취소마저 실패하면 관리자가 알 수 있게 사유에 남긴다. */
    private void refundAfterFailure(AdOrder order, Payment payment, RuntimeException cause) {
        log.error("광고하기 후속 처리 실패 (주문: {}): {}", order.getOrderId(), cause.toString());
        String reason = "후속 처리 실패(" + cause.getMessage() + ")로 자동 취소";
        try {
            tossPaymentsClient.cancelPayment(order.getPaymentKey(), "광고 접수 실패로 자동 취소");
            payment.setStatus(Payment.PaymentStatus.CANCELED);
            paymentRepository.save(payment);
        } catch (RuntimeException cancelError) {
            log.error("광고하기 자동 결제 취소에도 실패했습니다 - 관리자 확인 필요 (주문: {}): {}", order.getOrderId(), cancelError.toString());
            reason = "후속 처리 실패 + 자동 취소 실패 - 토스 결제를 수동으로 취소해야 해요 (" + cause.getMessage() + ")";
        }
        order.markFailed(reason);
        orderRepository.save(order);
    }

    /** 광고 접수: 이미 있으면 만료일을 늘리고(연장), 없으면 새로 시작한다. */
    private void activate(String listingId, Long userId, AdOrder order) {
        LocalDateTime now = LocalDateTime.now(clock);
        ListingAd ad = adRepository.findByListingId(listingId).orElse(null);
        if (ad == null) {
            ad = ListingAd.start(listingId, userId, now, order.getPeriodDays(), order.getOrderId());
        } else {
            ad.extend(now, order.getPeriodDays(), order.getOrderId());
        }
        adRepository.saveAndFlush(ad);
    }

    private AdConfirmResponse doneResponse(AdOrder order, boolean registered) {
        LocalDateTime expiresAt = adRepository.findByListingId(order.getListingId()).map(ListingAd::getExpiresAt).orElse(null);
        return new AdConfirmResponse(order.getOrderId(), order.getListingId(), registered, order.getAmount(), order.getPeriodDays(), expiresAt);
    }

    // ---------- 조회 ----------

    /** 내 광고 현황 (마이페이지 "등록한 매물 관리"에서 광고중/남은 기간/연장 버튼에 쓴다) */
    public List<MyAdResponse> myAds(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return adRepository.findByUserId(userId).stream().map(ad -> MyAdResponse.of(ad, now)).toList();
    }

    // ---------- 관리자 ----------

    public List<AdminAdOrderResponse> listOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AdOrder> orders = orderRepository.findAllByOrderByIdDesc();
        Map<Long, String> emails = userRepository.findAllById(orders.stream().map(AdOrder::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        List<String> listingIds = orders.stream().map(AdOrder::getListingId).filter(Objects::nonNull).distinct().toList();
        Map<String, ListingAd> ads = listingIds.isEmpty() ? Map.of()
                : adRepository.findByListingIdIn(listingIds).stream().collect(Collectors.toMap(ListingAd::getListingId, Function.identity()));
        return orders.stream().map(o -> AdAdminOrderMapper.toResponse(o, emails.get(o.getUserId()), ads.get(o.getListingId()), now)).toList();
    }

    /** 관리자: 지금 등록된 광고 매물 전부 (주문 없이 접수된 광고 포함, 만료일 늦은 순) */
    public List<AdminListingAdResponse> listAds() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ListingAd> ads = adRepository.findAll().stream()
                .sorted(Comparator.comparing(ListingAd::getExpiresAt).reversed()).toList();
        Map<Long, String> emails = userRepository.findAllById(ads.stream().map(ListingAd::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        return ads.stream().map(a -> new AdminListingAdResponse(a.getListingId(), a.getUserId(), emails.get(a.getUserId()),
                a.getStartedAt(), a.getExpiresAt(), a.isActive(now), a.getLastOrderId())).toList();
    }

    /** 관리자 환불: 토스 결제를 전액 취소하고, 그 주문이 늘려 준 노출 기간만큼 광고를 줄인다. 매물 자체는 그대로 유지된다. */
    public AdminAdOrderResponse cancel(String orderId) {
        AdOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "광고 주문을 찾을 수 없어요."));
        if (order.getStatus() != AdOrder.Status.DONE || order.getPaymentKey() == null) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "광고가 접수된 주문만 환불할 수 있어요.");
        }
        tossPaymentsClient.cancelPayment(order.getPaymentKey(), "관리자 환불");
        paymentRepository.findByOrderId(orderId).ifPresent(p -> {
            p.setStatus(Payment.PaymentStatus.CANCELED);
            paymentRepository.save(p);
        });
        LocalDateTime now = LocalDateTime.now(clock);
        ListingAd ad = adRepository.findByListingId(order.getListingId()).orElse(null);
        if (ad != null) {
            ad.shorten(now, order.getPeriodDays());
            adRepository.save(ad);
        }
        order.markCanceled("관리자 환불");
        orderRepository.save(order);
        activeAdService.evict();
        String email = userRepository.findById(order.getUserId()).map(User::getEmail).orElse(null);
        return AdAdminOrderMapper.toResponse(order, email, ad, now);
    }
}
