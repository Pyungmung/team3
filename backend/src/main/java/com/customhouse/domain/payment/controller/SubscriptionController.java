package com.customhouse.domain.payment.controller;

import com.customhouse.domain.payment.dto.BillingKeyRequest;
import com.customhouse.domain.payment.dto.SubscriptionResponse;
import com.customhouse.domain.payment.service.SubscriptionService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 황진구] 결제 도메인 - 맞집 프리미엄 월 정기 구독 API
 * 로그인(JWT)이 필요한 보호된 엔드포인트 (SecurityConfig: /api/payments/** -> authenticated()).
 */
@RestController
@RequestMapping("/api/payments/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/billing-key")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> issueBillingKey(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BillingKeyRequest request
    ) {
        SubscriptionResponse response = subscriptionService.issueBillingKey(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("자동결제 수단이 등록되었습니다.", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getMySubscription(principal.id())));
    }

    /** 정식 플로우는 스케줄러가 매일 자동 실행하지만, 데모/테스트를 위해 즉시 청구도 제공한다. */
    @PostMapping("/charge-now")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> chargeNow(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        SubscriptionResponse response = subscriptionService.chargeNow(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("자동결제가 실행되었습니다.", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> cancel(@AuthenticationPrincipal AuthenticatedUser principal) {
        subscriptionService.cancel(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("구독이 해지되었습니다.", null));
    }
}
