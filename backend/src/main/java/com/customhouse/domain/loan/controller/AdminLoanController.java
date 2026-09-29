package com.customhouse.domain.loan.controller;

import com.customhouse.domain.loan.dto.LoanListResponse;
import com.customhouse.domain.loan.dto.LoanProductRequest;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.customhouse.domain.loan.dto.LoanReferenceUrlRequest;
import com.customhouse.domain.loan.service.LoanProductService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 송귀성] 관리자 수정 > 전세자금대출 API. 관리자 전용이다:
 * SecurityConfig가 토큰의 role로 /api/admin/** 를 1차로 막고, 여기서 AdminGuard가 DB의 현재 role을 다시 확인한다.
 */
@RestController
@RequestMapping("/api/admin/loans")
@RequiredArgsConstructor
public class AdminLoanController {

    private final LoanProductService loanProductService;
    private final AdminGuard adminGuard;

    /** 대출 5종의 현재 조건 (저장 안 된 대출은 saved=false). */
    @GetMapping
    public ResponseEntity<ApiResponse<LoanListResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok(loanProductService.getAll()));
    }

    /** 수정완료: 해당 대출의 조건을 저장한다. */
    @PutMapping("/{type}")
    public ResponseEntity<ApiResponse<LoanProductResponse>> save(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String type,
            @Valid @RequestBody LoanProductRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("저장했습니다.", loanProductService.save(type, request)));
    }

    /** 참고 확인 페이지 주소만 저장한다 (자격 조건과 무관, 그 대출을 조사할 때 참고한 링크를 보관만 한다). */
    @PutMapping("/{type}/reference-url")
    public ResponseEntity<ApiResponse<LoanProductResponse>> saveReferenceUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String type,
            @Valid @RequestBody LoanReferenceUrlRequest request
    ) {
        adminGuard.requireAdmin(principal.id());
        return ResponseEntity.ok(ApiResponse.ok("참고 페이지 주소를 저장했습니다.", loanProductService.saveReferenceUrl(type, request)));
    }

    /** 삭제: 저장된 조건을 지운다. */
    @DeleteMapping("/{type}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String type
    ) {
        adminGuard.requireAdmin(principal.id());
        loanProductService.delete(type);
        return ResponseEntity.ok(ApiResponse.ok("삭제했습니다.", null));
    }
}
