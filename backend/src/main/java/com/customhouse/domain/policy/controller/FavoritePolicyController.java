package com.customhouse.domain.policy.controller;

import com.customhouse.domain.policy.dto.FavoritePolicyRequest;
import com.customhouse.domain.policy.dto.FavoritePolicyResponse;
import com.customhouse.domain.policy.service.FavoritePolicyService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [담당: 송귀성] 주거정책 - 관심정책 API. 리포트 "주거정책 추천" 표의 하트와 관심매물 페이지 "관심정책 조회"가 쓴다.
 * /api/watchlist/** 는 SecurityConfig에서 로그인 필요로 묶여 있다 (관심매물 ListingFavoriteController와 같은 경로 아래).
 */
@RestController
@RequestMapping("/api/watchlist/policies")
@RequiredArgsConstructor
public class FavoritePolicyController {

    private final FavoritePolicyService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody FavoritePolicyRequest request
    ) {
        favoriteService.add(principal.id(), request.policyId());
        return ResponseEntity.ok(ApiResponse.ok("관심 정책으로 담았어요.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoritePolicyResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getMyFavorites(principal.id())));
    }

    /** 리포트 표 하트 표시용: 내가 담은 정책 id 목록만 가볍게 돌려준다. */
    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<Long>>> ids(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getMyPolicyIds(principal.id())));
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long policyId
    ) {
        favoriteService.remove(principal.id(), policyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
