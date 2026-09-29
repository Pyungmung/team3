package com.customhouse.domain.incomestandard.controller;

import com.customhouse.domain.incomestandard.dto.IncomeStandardRequest;
import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.incomestandard.service.IncomeStandardService;
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
 * [담당: 송귀성] 관리자 수정 > 기준소득관리 API. 관리자 전용이다:
 * SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고, 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다.
 */
@RestController
@RequestMapping("/api/admin/income-standard")
@RequiredArgsConstructor
public class AdminIncomeStandardController {

    private final IncomeStandardService incomeStandardService;
    private final AdminGuard adminGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<IncomeStandardResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(incomeStandardService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<IncomeStandardResponse>> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody IncomeStandardRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("저장했습니다.", incomeStandardService.save(request)));
    }
}
