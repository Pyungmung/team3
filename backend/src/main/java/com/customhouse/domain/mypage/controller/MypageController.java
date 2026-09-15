package com.customhouse.domain.mypage.controller;

import com.customhouse.domain.mypage.dto.MypageConditionRequest;
import com.customhouse.domain.mypage.dto.MypageConditionResponse;
import com.customhouse.domain.mypage.service.MypageService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 황진구] 마이페이지 도메인 - 주거 조건 조회/저장 API
 * 로그인(JWT)이 필요한 보호된 엔드포인트 (SecurityConfig: /api/mypage/** -> authenticated()).
 */
@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService mypageService;

    @GetMapping("/condition")
    public ResponseEntity<ApiResponse<MypageConditionResponse>> getCondition(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(mypageService.getMyCondition(principal.id())));
    }

    @PutMapping("/condition")
    public ResponseEntity<ApiResponse<MypageConditionResponse>> saveCondition(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody MypageConditionRequest request
    ) {
        MypageConditionResponse response = mypageService.saveMyCondition(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("주거 조건이 저장되었습니다.", response));
    }
}
