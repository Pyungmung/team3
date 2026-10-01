package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.dto.ListingReportCountsRequest;
import com.customhouse.domain.listing.dto.ListingReportRequest;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.service.ListingReportService;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 API + 실거래 참고/통근시간 조회.
 * 신고는 로그인 필요, 신고 수 조회(counts)·실거래 참고·통근시간 조회는 누구나 가능하다 (SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingReportController {

    private final ListingReportService reportService;
    private final AiEngineClient aiEngineClient;

    @PostMapping("/{listingId}/reports")
    public ResponseEntity<ApiResponse<ListingReportStatus>> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String listingId,
            @Valid @RequestBody ListingReportRequest request
    ) {
        ListingReportStatus status = reportService.report(principal.id(), listingId, request);
        return ResponseEntity.ok(ApiResponse.ok("신고가 접수되었습니다. 확인 후 조치할게요.", status));
    }

    /** 카드 목록의 매물번호들을 보내면 신고가 있는 매물의 현황만 돌려준다. */
    @PostMapping("/reports/counts")
    public ResponseEntity<ApiResponse<Map<String, ListingReportStatus>>> counts(
            @Valid @RequestBody ListingReportCountsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getStatuses(request.listingIds())));
    }

    /**
     * 매물 카드를 펼칠 때 그 매물의 "실거래 참고"를 실시간 조회한다 (AI 엔진에서 매번 국토부 API로 조회).
     * 응답은 {scope, transactions} 형태를 그대로 전달한다.
     */
    @GetMapping("/{listingId}/reference")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reference(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(aiEngineClient.fetchReferenceTransactions(listingId)));
    }

    /**
     * 매물 카드의 "통근 약 ~분"은 추천 응답에선 전부 직선거리 추정치다(검색/매칭을 가볍게 하려고,
     * AiEngineClient.fetchListingCommute 참고). 화면에 보이는(스크롤로 로딩된) 카드에 대해서만
     * 이 API로 그 매물 하나의 정확한 카카오 API 통근시간을 그때그때 불러와 표시를 갱신한다.
     */
    @GetMapping("/{listingId}/commute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commute(
            @PathVariable String listingId,
            @RequestParam double workLat,
            @RequestParam double workLon,
            @RequestParam(required = false) String transportType
    ) {
        return ResponseEntity.ok(ApiResponse.ok(aiEngineClient.fetchListingCommute(listingId, workLat, workLon, transportType)));
    }
}
