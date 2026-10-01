package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.dto.ListingRegistrationResponse;
import com.customhouse.domain.listing.service.ListingRegistrationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] 회원 매물 등록/조회/수정/삭제. 등록·내 매물 조회·수정은 로그인한 회원(수정은 등록한
 * 본인만), 삭제는 등록한 본인 또는 관리자만 가능하다 (SecurityConfig의 "/api/listings/**" 기본 규칙
 * = 로그인 필요를 그대로 쓴다).
 */
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingRegistrationController {

    private final ListingRegistrationService registrationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ListingRegistrationResponse>> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ListingRegistrationRequest request
    ) {
        ListingRegistrationResponse result = registrationService.register(principal.id(), request);
        return ResponseEntity.ok(ApiResponse.ok("매물이 등록되었습니다.", result));
    }

    /** 마이페이지 "등록한 매물 관리" 탭 - 진단 조건과 무관하게 내가 등록한 매물 전부. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> mine(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(registrationService.listMine(principal.id())));
    }

    /** 매물 1건의 원본 정보 (수정 폼 프리필용). */
    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(registrationService.getDetail(listingId)));
    }

    @PutMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingRegistrationResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String listingId,
            @Valid @RequestBody ListingRegistrationRequest request
    ) {
        ListingRegistrationResponse result = registrationService.update(principal.id(), listingId, request);
        return ResponseEntity.ok(ApiResponse.ok("매물이 수정되었습니다.", result));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String listingId,
            @RequestParam(required = false) String region
    ) {
        registrationService.delete(principal.id(), listingId, region);
        return ResponseEntity.ok(ApiResponse.ok("매물이 삭제되었습니다.", null));
    }
}
