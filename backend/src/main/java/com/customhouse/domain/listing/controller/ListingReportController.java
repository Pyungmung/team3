package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.dto.ListingReportCountsRequest;
import com.customhouse.domain.listing.dto.ListingReportRequest;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.service.ListingReportService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 API.
 * 신고는 로그인 필요, 신고 수 조회(counts)는 누구나 가능하다 (SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingReportController {

    private final ListingReportService reportService;

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
}
