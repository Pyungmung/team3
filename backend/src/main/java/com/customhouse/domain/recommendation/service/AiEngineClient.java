package com.customhouse.domain.recommendation.service;

import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.customhouse.domain.loan.service.LoanProductService;
import com.customhouse.domain.recommendation.dto.AiListingRequest;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - Python 엔진(customhouse-ai) 연동 클라이언트
 * "AI API & 주거비 절약 추천 링크 구성 알고리즘" 중 백엔드 ↔ AI 엔진 연결부.
 * customhouse-ai의 POST /api/v1/diagnosis 엔드포인트를 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiEngineClient {

    private final RestClient aiEngineRestClient;
    private final LoanProductService loanProductService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestDiagnosis(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    /**
     * 더미 매물(docs/samples/dummyhouses CSV) 기반 추천. 같은 요청 본문을 customhouse-ai의
     * POST /api/v1/diagnosis/listings로 넘긴다 (국토부 실거래가는 매물별 참고 정보로만 내려온다).
     * 관리자 화면(관리자 수정 > 전세자금대출)에서 저장한 대출 조건을 함께 실어 보내면 AI 엔진이 매물마다 신청 가능한 대출을 판별한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestListingDiagnosis(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiListingRequest(request, savedLoans()))
                .retrieve()
                .body(Map.class);
    }

    /** 대출 조건을 읽지 못해도 추천 자체는 계속한다 (대출 표시만 빠진다). */
    private List<LoanProductResponse> savedLoans() {
        try {
            return loanProductService.getSavedLoans();
        } catch (RuntimeException e) {
            log.warn("대출 조건을 읽지 못해 대출 없이 추천을 진행합니다: {}", e.getMessage());
            return List.of();
        }
    }
}
