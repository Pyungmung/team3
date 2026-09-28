package com.customhouse.domain.watchlist.controller;

import com.customhouse.domain.watchlist.dto.AddWatchlistRequest;
import com.customhouse.domain.watchlist.dto.PriceRecheckRequest;
import com.customhouse.domain.watchlist.dto.WatchlistItemResponse;
import com.customhouse.domain.watchlist.service.WatchlistService;
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
 * [담당: 김시연] WatchList 도메인 - 관심 매물 등록·조회·삭제 · 변동 감지 · 신고 API
 */
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    public ResponseEntity<ApiResponse<WatchlistItemResponse>> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddWatchlistRequest request
    ) {
        WatchlistItemResponse response = watchlistService.addToWatchlist(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("관심 매물로 등록되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(watchlistService.getMyWatchlist(principal.id())));
    }

    @DeleteMapping("/{watchlistItemId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long watchlistItemId
    ) {
        watchlistService.removeFromWatchlist(principal.id(), watchlistItemId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/properties/{propertyId}/recheck-price")
    public ResponseEntity<ApiResponse<WatchlistItemResponse>> recheckPrice(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long propertyId,
            @Valid @RequestBody PriceRecheckRequest request
    ) {
        WatchlistItemResponse response = watchlistService.recheckPrice(principal.id(), propertyId, request);
        return ResponseEntity.ok(ApiResponse.ok("시세 재확인이 완료되었습니다.", response));
    }

    @PostMapping("/properties/{propertyId}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long propertyId
    ) {
        watchlistService.report(principal.id(), propertyId);
        return ResponseEntity.ok(ApiResponse.ok("신고가 접수되었습니다.", null));
    }
}
