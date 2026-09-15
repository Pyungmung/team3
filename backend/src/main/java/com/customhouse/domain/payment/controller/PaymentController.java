package com.customhouse.domain.payment.controller;

import com.customhouse.domain.payment.dto.PaymentConfirmRequest;
import com.customhouse.domain.payment.dto.PaymentResponse;
import com.customhouse.domain.payment.service.PaymentService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 황진구] 결제 도메인 - 안심 매물 리포트 단건 결제 API
 * 로그인(JWT)이 필요한 보호된 엔드포인트 (SecurityConfig: /api/payments/** -> authenticated()).
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentResponse response = paymentService.confirm(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("결제가 완료되었습니다.", response));
    }
}
