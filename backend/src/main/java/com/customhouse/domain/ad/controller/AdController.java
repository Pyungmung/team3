package com.customhouse.domain.ad.controller;

import com.customhouse.domain.ad.dto.AdConfigResponse;
import com.customhouse.domain.ad.dto.AdConfirmResponse;
import com.customhouse.domain.ad.dto.AdOrderRequest;
import com.customhouse.domain.ad.dto.AdOrderResponse;
import com.customhouse.domain.ad.dto.MyAdResponse;
import com.customhouse.domain.ad.service.AdService;
import com.customhouse.domain.payment.dto.PaymentConfirmRequest;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [담당: 송귀성] 광고하기 API (2026-10-08). 전부 로그인 필요 (SecurityConfig: /api/ads/** -> authenticated()).
 * 결제 금액/노출 기간은 서버가 정하고(관리자 기타 설정), 프론트는 주문을 만들어 토스 결제창을 연 뒤 돌아와 승인만 요청한다.
 */
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    /** 현재 광고 가격/노출 기간과 토스 클라이언트 키 */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AdConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.ok(adService.config()));
    }

    /** 결제 전 주문 생성 (이미 등록한 내 매물 광고 / 매물 등록 + 광고) */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<AdOrderResponse>> createOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AdOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("광고 주문이 만들어졌어요.", adService.createOrder(principal.id(), request)));
    }

    /** 토스 결제창에서 돌아온 뒤 결제 승인 + 매물 등록 + 광고 접수. 같은 주문을 다시 불러도 같은 결과를 돌려준다. */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<AdConfirmResponse>> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("광고가 접수되었어요.", adService.confirm(principal.id(), request)));
    }

    /** 내 매물의 광고 현황 (광고중 여부, 만료일, 남은 일수) */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<MyAdResponse>>> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(adService.myAds(principal.id())));
    }
}
