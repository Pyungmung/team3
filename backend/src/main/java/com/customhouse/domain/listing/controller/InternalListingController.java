package com.customhouse.domain.listing.controller;

import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] AI 엔진 전용 내부 API (2026-10-06). 엔진이 서버가 켜질 때 회원 등록 매물 원본(CSV 행 55컬럼)을 돌려받아
 * CSV에 되살리는 데 쓴다 (customhouse-ai의 services/listing_sync.py). 일반 회원/관리자 로그인이 아니라 두 서버가
 * 함께 아는 비밀키(INTERNAL_API_KEY, 요청 헤더 X-Internal-Key)로만 열린다. 키가 설정돼 있지 않으면 항상 거절한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalListingController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RegisteredListingRepository registeredListingRepository;

    @Value("${internal.api-key:}")
    private String internalApiKey;

    @GetMapping("/registered-listings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> registeredListings(
            @RequestHeader(value = "X-Internal-Key", required = false) String key
    ) {
        requireKey(key);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RegisteredListing owned : registeredListingRepository.findAll()) {
            if (owned.getRowJson() == null || owned.getRowJson().isBlank()) {
                continue;   // 원본을 저장하기 전에 등록된 옛 기록
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = JSON.readValue(owned.getRowJson(), Map.class);
                rows.add(row);
            } catch (RuntimeException e) {
                log.warn("저장된 매물 원본을 읽지 못해 건너뜁니다 ({}): {}", owned.getListingId(), e.toString());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    void requireKey(String key) {
        if (internalApiKey == null || internalApiKey.isBlank() || key == null
                || !MessageDigest.isEqual(internalApiKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
