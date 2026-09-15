package com.customhouse.domain.recommendation.service;

import com.customhouse.domain.recommendation.dto.RecommendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - Python 엔진(customhouse-ai) 연동 클라이언트
 * "AI API & 주거비 절약 추천 링크 구성 알고리즘" 중 백엔드 ↔ AI 엔진 연결부.
 * customhouse-ai의 POST /api/v1/diagnosis 엔드포인트를 호출한다.
 */
@Component
@RequiredArgsConstructor
public class AiEngineClient {

    private final RestClient aiEngineRestClient;

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestDiagnosis(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}
