package com.customhouse.domain.recommendation.service;

import com.customhouse.domain.ad.service.ActiveAdService;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.service.AppSettingService;
import com.customhouse.domain.housingpolicy.dto.HousingPolicyForEngine;
import com.customhouse.domain.housingpolicy.service.HousingPolicyService;
import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.incomestandard.service.IncomeStandardService;
import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.dto.ListingRegistrationResponse;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.customhouse.domain.loan.service.LoanProductService;
import com.customhouse.domain.recommendation.dto.AiListingRequest;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * [담당: 송귀성] AI 주거비 절약 추천 - Python 엔진(customhouse-ai) 연동 클라이언트
 * "AI API & 주거비 절약 추천 링크 구성 알고리즘" 중 백엔드 ↔ AI 엔진 연결부.
 * customhouse-ai의 POST /api/v1/diagnosis/listings, GET /api/v1/listings/{listingId}/reference,
 * GET /api/v1/listings/{listingId}/commute 엔드포인트를 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiEngineClient {

    private final RestClient aiEngineRestClient;
    private final LoanProductService loanProductService;
    private final IncomeStandardService incomeStandardService;
    private final AppSettingService appSettingService;
    private final HousingPolicyService housingPolicyService;
    private final ActiveAdService activeAdService;

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
                .body(new AiListingRequest(request, savedLoans(), incomeStandard(), appSettings(), housingPolicies(), activeAdListingIds()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 메인 홈의 "AI 주거 진단 미리보기"용 가벼운 요약 (2026-10-06) - 전체 진단과 달리 대출 조건/기타 설정은 읽지 않고(DB 조회 없음, 기준소득만 캐시된 값을 쓴다)
     * 요약 몇 개(적정 월세 상한, 주거비 비율, 추천 지역, 평균 통근시간)만 돌려받는다. customhouse-ai POST /api/v1/diagnosis/home-preview.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestHomePreview(RecommendRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/diagnosis/home-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiListingRequest(request, List.of(), incomeStandard(), null, List.of(), List.of()))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 관심매물 새로고침 - 매물번호 1건을 사용자의 현재 조건으로 다시 계산한 카드를 받는다
     * (customhouse-ai POST /api/v1/diagnosis/listings/{listingId}). 같은 대출 조건/기준소득을 함께 실어 보낸다.
     * 매물이 없으면(삭제됨) AI 엔진이 404를 주고, 여기서 NOT_FOUND로 바꿔 던진다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestListingRefresh(String listingId, RecommendRequest request) {
        try {
            return aiEngineRestClient.post()
                    .uri("/api/v1/diagnosis/listings/{listingId}", listingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiListingRequest(request, savedLoans(), incomeStandard(), appSettings(), housingPolicies(), activeAdListingIds()))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new CustomException(ErrorCode.NOT_FOUND, "매물을 찾을 수 없어요. 삭제되었을 수 있어요.");
        }
    }

    /**
     * 매물 카드의 "실거래 참고"를 실시간 조회한다 (customhouse-ai GET /api/v1/listings/{listingId}/reference).
     * 응답은 {scope: "building"|"neighborhood"|"none", transactions: [...]} 형태 - scope로 프론트가
     * "이 건물의 실거래"인지 "동 단위 참고"인지 구분해서 보여준다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchReferenceTransactions(String listingId) {
        return aiEngineRestClient.get()
                .uri("/api/v1/listings/{listingId}/reference", listingId)
                .retrieve()
                .body(Map.class);
    }

    /**
     * 매물 카드에 보이는 "통근 약 ~분"은 추천 응답(requestListingDiagnosis)에선 전부 직선거리 추정치다
     * (검색/매칭을 가볍게 하려고 2026-10-01부터 그렇게 바뀜). 화면에 "보이는"(스크롤로 로딩된) 카드에
     * 대해서만 이 메서드로 그 매물 하나의 정확한 카카오 API 통근시간을 그때그때 불러온다.
     * 응답은 {commute_minutes, commute_source} 형태를 그대로 전달한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchListingCommute(String listingId, double workLat, double workLon, String transportType) {
        return aiEngineRestClient.get()
                .uri("/api/v1/listings/{listingId}/commute?workLat={workLat}&workLon={workLon}&transportType={transportType}",
                        listingId, workLat, workLon, transportType)
                .retrieve()
                .body(Map.class);
    }

    /** 회원이 입력한 매물을 AI 엔진이 주소를 해석해 그 자치구 CSV에 등록하게 한다. */
    public ListingRegistrationResponse registerListing(ListingRegistrationRequest request) {
        return aiEngineRestClient.post()
                .uri("/api/v1/listings/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ListingRegistrationResponse.class);
    }

    /**
     * 매물 1건의 원본 정보를 그대로 가져온다(통근시간/실질주거비 같은 진단 계산값 없음) - 마이페이지
     * "등록한 매물 관리" 탭과 매물 수정 폼 프리필용. 없으면(이미 다른 경로로 지워졌거나 잘못된 번호) null.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getListing(String listingId) {
        try {
            return aiEngineRestClient.get()
                    .uri("/api/v1/listings/{listingId}", listingId)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** 매물을 수정한다. register와 같은 요청 바디 - 주소도 다시 해석되어 좌표/법정동/지번이 갱신된다. */
    public ListingRegistrationResponse updateListing(String listingId, ListingRegistrationRequest request) {
        return aiEngineRestClient.put()
                .uri("/api/v1/listings/{listingId}", listingId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ListingRegistrationResponse.class);
    }

    /** 매물을 삭제 상태로 바꾼다(물리 삭제가 아니라 추천에서 제외되는 상태로 CSV 행을 갱신). */
    public void markListingDeleted(String listingId, String region) {
        aiEngineRestClient.put()
                .uri("/api/v1/listings/{listingId}/status", listingId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("region", region, "status", "삭제됨"))
                .retrieve()
                .toBodilessEntity();
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

    /** 주거지원정책(리포트 "주거정책 추천")을 읽지 못해도 진단 자체는 계속한다 (정책 표만 비어 보인다). */
    private List<HousingPolicyForEngine> housingPolicies() {
        try {
            return housingPolicyService.getForEngine();
        } catch (RuntimeException e) {
            log.warn("주거지원정책을 읽지 못해 정책 추천 없이 진단을 진행합니다: {}", e.getMessage());
            return List.of();
        }
    }

    /** 광고 중인 매물번호를 읽지 못해도 진단 자체는 계속한다 (광고가 안 끼워질 뿐). */
    private List<String> activeAdListingIds() {
        try {
            return activeAdService.activeListingIds();
        } catch (RuntimeException e) {
            log.warn("광고 중인 매물을 읽지 못해 광고 없이 진단을 진행합니다: {}", e.getMessage());
            return List.of();
        }
    }

    /** 기타 설정(추천 개수 상한)을 읽지 못해도 진단 자체는 계속한다 (AI 엔진이 기본값으로 대신한다). */
    private AppSettingResponse appSettings() {
        try {
            return appSettingService.get();
        } catch (RuntimeException e) {
            log.warn("기타 설정을 읽지 못해 기본값으로 진단을 진행합니다: {}", e.getMessage());
            return null;
        }
    }
}
