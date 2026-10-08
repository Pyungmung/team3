package com.customhouse.domain.banner.controller;

import com.customhouse.domain.banner.dto.BannerRequest;
import com.customhouse.domain.banner.dto.BannerResponse;
import com.customhouse.domain.banner.service.BannerService;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [담당: 송귀성] 관리자 수정 > 배너 광고 API (2026-10-08). 관리자 전용: SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고,
 * 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다 (AdminAdController와 같은 방식).
 */
@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;
    private final AdminGuard adminGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(bannerService.listAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BannerRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("배너를 등록했어요.", bannerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody BannerRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("배너를 수정했어요.", bannerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        adminGuard.requireAdmin(principal.id());
        bannerService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("배너를 삭제했어요.", null));
    }
}
