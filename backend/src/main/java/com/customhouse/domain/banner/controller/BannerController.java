package com.customhouse.domain.banner.controller;

import com.customhouse.domain.banner.dto.BannerResponse;
import com.customhouse.domain.banner.service.BannerService;
import com.customhouse.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [담당: 송귀성] 직접 배너 광고 공개 API (2026-10-08). 로그인 없이 호출할 수 있다(비로그인 방문자에게도 광고 자리가 채워져야 한다).
 * 어떤 방문자에게 어떤 배너를 보일지는 프론트(ad-targeting.js)가 이 목록으로 판정한다.
 */
@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(bannerService.listPublic()));
    }
}
