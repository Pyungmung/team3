package com.customhouse.domain.recommendation.service;

import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.incomestandard.service.IncomeStandardService;
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
    private final IncomeStandardService incomeStandardService;

    /** 국토부 실거래가 기반 추천. 대출 매칭은 이 경로엔 없어 loanProducts는 항상 빈 목록이다 - 기준소득(RIR/중위소득)은 함께 실어 보낸다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestDiagnosis(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiListingRequest(request, List.of(), incomeStandard()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 더미 매물(docs/samples/dummyhouses CSV) 기반 추천. 같은 요청 본문을 customhouse-ai의
     * POST /api/v1/diagnosis/listings로 넘긴다 (국토부 실거래가는 매물별 참고 정보로만 내려온다).
     * 관리자 화면(관리자 수정 > 전세자금대출)에서 저장한 대출 조건, (관리자 수정 > 기준소득관리)에서 저장한 RIR/기준중위소득을
     * 함께 실어 보내면 AI 엔진이 매물마다 신청 가능한 대출을 판별하고 적정 월세/정책 소득기준을 계산한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestListingDiagnosis(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiListingRequest(request, savedLoans(), incomeStandard()))
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

    /** 기준소득 통계를 읽지 못해도 진단 자체는 계속한다 (AI 엔진이 CSV/JSON 폴백으로 대신한다). */
    private IncomeStandardResponse incomeStandard() {
        try {
            return incomeStandardService.get();
        } catch (RuntimeException e) {
            log.warn("기준소득 통계를 읽지 못해 폴백 없이 진단을 진행합니다: {}", e.getMessage());
            return null;
        }
    }
}
