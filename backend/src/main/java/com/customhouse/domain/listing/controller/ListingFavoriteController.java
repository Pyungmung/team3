package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.dto.ListingFavoriteRequest;
import com.customhouse.domain.listing.dto.ListingFavoriteResponse;
import com.customhouse.domain.listing.service.ListingFavoriteService;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
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
 * [담당: 송귀성] 추천 매물 - 관심매물 API. 마이페이지 "관심 매물"(watchlist)에 함께 보이며,
 * 기존 WatchlistController(/api/watchlist)와는 경로만 이어지고 코드/테이블은 분리되어 있다.
 * /api/watchlist/** 는 SecurityConfig에서 로그인 필요로 묶여 있다.
 */
@RestController
@RequestMapping("/api/watchlist/listings")
@RequiredArgsConstructor
public class ListingFavoriteController {

    private final ListingFavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ListingFavoriteRequest request
    ) {
        favoriteService.add(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("관심 매물로 등록되었습니다.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ListingFavoriteResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getMyFavorites(principal.id())));
    }

    /** 리포트 카드 하트 표시용: 내가 담은 매물번호 목록만 가볍게 돌려준다. */
    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<String>>> ids(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.getMyListingIds(principal.id())));
    }

    /** 관심매물 새로고침 - 요청 본문은 AI 주거진단 때 입력한 조건(리포트가 보내는 것과 같은 모양). */
    @PostMapping("/{listingId}/refresh")
    public ResponseEntity<ApiResponse<ListingFavoriteResponse>> refresh(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String listingId,
            @Valid @RequestBody RecommendRequest condition
    ) {
        return ResponseEntity.ok(ApiResponse.ok("새 정보로 새로고침했어요.", favoriteService.refresh(principal.id(), listingId, condition)));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String listingId
    ) {
        favoriteService.remove(principal.id(), listingId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
