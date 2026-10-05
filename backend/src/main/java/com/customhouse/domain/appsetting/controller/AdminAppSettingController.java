package com.customhouse.domain.appsetting.controller;

import com.customhouse.domain.appsetting.dto.AppSettingRequest;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.service.AppSettingService;
import com.customhouse.domain.user.service.AdminGuard;
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
 * [담당: 송귀성] 관리자 수정 > 기타 설정 API. 관리자 전용이다:
 * SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고, 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다.
 */
@RestController
@RequestMapping("/api/admin/app-settings")
@RequiredArgsConstructor
public class AdminAppSettingController {

    private final AppSettingService appSettingService;
    private final AdminGuard adminGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<AppSettingResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(appSettingService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AppSettingResponse>> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AppSettingRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("저장했습니다.", appSettingService.save(request)));
    }
}
