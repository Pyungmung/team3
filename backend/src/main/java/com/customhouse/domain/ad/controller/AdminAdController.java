package com.customhouse.domain.ad.controller;

import com.customhouse.domain.ad.dto.AdminAdOrderResponse;
import com.customhouse.domain.ad.service.AdService;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [담당: 송귀성] 관리자 수정 > 광고 현황 API (2026-10-08): 광고 주문 목록과 환불(토스 결제 취소). 관리자 전용이다:
 * SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고, 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다.
 */
@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
public class AdminAdController {

    private final AdService adService;
    private final AdminGuard adminGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAdOrderResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(adService.listOrders()));
    }

    /** 환불: 토스 결제 전액 취소 + 그 주문이 늘려 준 광고 기간 차감 (매물은 유지) */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<AdminAdOrderResponse>> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String orderId
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("환불했어요.", adService.cancel(orderId)));
    }
}
