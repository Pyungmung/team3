package com.customhouse.domain.recommendation.service;

import com.customhouse.domain.incomestandard.service.IncomeStandardService;
import com.customhouse.domain.loan.service.LoanProductService;
import com.customhouse.domain.recommendation.dto.AiListingRequest;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] AI 엔진 클라이언트 테스트: 더미 매물 추천 요청에 서버가 읽은 대출 조건/기준소득 통계가 붙고,
 * 둘 중 하나를 못 읽어도 추천 요청은 계속되는지 확인한다.
 */
class AiEngineClientTest {

    private final RecommendRequest request = new RecommendRequest(3400, null, 800, null, null, "강남구", null, null, 40,
            29, true, null, null, List.of(), null, "PUBLIC", true, List.of());

    private static RestClient.RequestBodySpec mockRestClient(RestClient rest, Map<String, Object> responseBody) {
        RestClient.RequestBodyUriSpec post = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec spec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(rest.post()).thenReturn(post);
        when(post.uri(any(String.class))).thenReturn(spec);
        when(spec.contentType(any())).thenReturn(spec);
        when(spec.body(any(Object.class))).thenReturn(spec);
        when(spec.retrieve()).thenReturn(response);
        when(response.body(Map.class)).thenReturn(responseBody);
        return spec;
    }

    @Test
    void 대출_조건을_읽지_못해도_빈_목록으로_추천을_계속한다() {
        RestClient rest = mock(RestClient.class);
        RestClient.RequestBodySpec spec = mockRestClient(rest, Map.of("ok", true));
        LoanProductService loans = mock(LoanProductService.class);
        when(loans.getSavedLoans()).thenThrow(new IllegalStateException("db down"));
        IncomeStandardService incomeStandard = mock(IncomeStandardService.class);

        Map<String, Object> result = new AiEngineClient(rest, loans, incomeStandard).requestListingDiagnosis(request);

        assertThat(result).containsEntry("ok", true);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(spec).body(body.capture());
        assertThat(body.getValue()).isInstanceOf(AiListingRequest.class);
        assertThat(((AiListingRequest) body.getValue()).loanProducts()).isEmpty();
    }

    @Test
    void 기준소득_통계를_읽지_못해도_null로_추천을_계속한다() {
        RestClient rest = mock(RestClient.class);
        RestClient.RequestBodySpec spec = mockRestClient(rest, Map.of("ok", true));
        LoanProductService loans = mock(LoanProductService.class);
        when(loans.getSavedLoans()).thenReturn(List.of());
        IncomeStandardService incomeStandard = mock(IncomeStandardService.class);
        when(incomeStandard.get()).thenThrow(new IllegalStateException("db down"));

        Map<String, Object> result = new AiEngineClient(rest, loans, incomeStandard).requestListingDiagnosis(request);

        assertThat(result).containsEntry("ok", true);
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(spec).body(body.capture());
        assertThat(((AiListingRequest) body.getValue()).incomeStandard()).isNull();
    }
}
