package com.customhouse.domain.housingpolicy.controller;

import com.customhouse.domain.housingpolicy.dto.HousingPolicyRequest;
import com.customhouse.domain.housingpolicy.dto.HousingPolicyResponse;
import com.customhouse.domain.housingpolicy.dto.HousingPolicySaveResponse;
import com.customhouse.domain.housingpolicy.service.HousingPolicyService;
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
 * [담당: 송귀성] 관리자 수정 > 주거지원정책 API. 관리자 전용이다:
 * SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고, 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다.
 */
@RestController
@RequestMapping("/api/admin/housing-policies")
@RequiredArgsConstructor
public class AdminHousingPolicyController {

    private final HousingPolicyService policyService;
    private final AdminGuard adminGuard;

    /** 전체 정책 (화면에서 서울공통/자치구 하위탭으로 나눠 보여준다) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<HousingPolicyResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(policyService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HousingPolicySaveResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody HousingPolicyRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("추가했습니다.", policyService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HousingPolicySaveResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody HousingPolicyRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("저장했습니다.", policyService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<HousingPolicySaveResponse>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("삭제했습니다.", policyService.delete(id)));
    }
}
