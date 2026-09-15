package com.customhouse.domain.recommendation.controller;

import com.customhouse.domain.recommendation.dto.RecommendRequest;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - 프론트엔드(양혜승) → 백엔드 → AI 엔진으로 이어지는 핵심 API.
 * 이 서비스의 핵심 기능(맞집의 "실질 주거비 계산 + 지역 매칭 + 정책 추천")이 여기서 시작된다.
 */
@RestController
@RequestMapping("/api/recommendation")
@RequiredArgsConstructor
public class RecommendationController {

    private final AiEngineClient aiEngineClient;

    @PostMapping("/diagnosis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diagnose(@Valid @RequestBody RecommendRequest request) {
        Map<String, Object> result = aiEngineClient.requestDiagnosis(request);
        return ResponseEntity.ok(ApiResponse.ok("주거비 절약 진단이 완료되었습니다.", result));
    }
}
