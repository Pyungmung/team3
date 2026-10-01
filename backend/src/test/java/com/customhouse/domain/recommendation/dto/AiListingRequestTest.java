package com.customhouse.domain.recommendation.dto;

import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.loan.dto.LoanPreference;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [담당: 송귀성] AI 엔진으로 보내는 더미 매물 추천 요청 본문 테스트.
 * 브라우저 조건(RecommendRequest)이 최상위에 그대로 펼쳐지고, 서버가 붙인 대출 조건(loanProducts)이 함께 실리는지 확인한다.
 */
class AiListingRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private RecommendRequest recommend() {
        return new RecommendRequest(3400, null, 800, 20000, null, "강남구", 37.5, 127.03, 40, 29, true, null, null, "SME",
                List.of("NEWLYWED"), "EXPLORING", "PUBLIC", true, List.of("아파트"));
    }

    @Test
    void 사용자_조건은_최상위로_펼쳐지고_대출_조건이_함께_실린다() throws Exception {
        LoanProductResponse loan = new LoanProductResponse("GENERAL_BEOTIMMOK", "일반 버팀목 전세대출", "전세", true,
                19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000,
                Map.of("NEWLYWED", new LoanPreference(true, 0.2, null, null, null, null, null, null)), null, null, null);

        IncomeStandardResponse incomeStandard = new IncomeStandardResponse(15.8, 18.4, 18.3, 16.2, 19.4, 2024, "국토교통부,「주거실태조사」", 2_564_238L, null);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new AiListingRequest(recommend(), List.of(loan), incomeStandard)));

        // 기존 요청 필드는 AI 엔진이 기대하는 이름 그대로 최상위에 있다 (감싸이지 않는다)
        assertThat(json.get("annualIncome").asInt()).isEqualTo(3400);
        assertThat(json.get("workLocation").asText()).isEqualTo("강남구");
        assertThat(json.get("jobType").asText()).isEqualTo("SME");
        assertThat(json.has("request")).isFalse();
        // 대출 조건
        JsonNode l = json.get("loanProducts").get(0);
        assertThat(l.get("type").asText()).isEqualTo("GENERAL_BEOTIMMOK");
        assertThat(l.get("leaseType").asText()).isEqualTo("전세");
        assertThat(l.get("maxListingDeposit").asInt()).isEqualTo(20000);
        assertThat(l.get("maxExclusiveArea").asDouble()).isEqualTo(85.0);
        assertThat(l.get("maxLoanRatioPercent").asDouble()).isEqualTo(80.0);
        assertThat(l.get("maxLoanAmount").asInt()).isEqualTo(20000);
        assertThat(l.get("preferences").get("NEWLYWED").get("required").asBoolean()).isTrue();
        assertThat(l.get("preferences").get("NEWLYWED").get("discount").asDouble()).isEqualTo(0.2);
        // 기준소득 통계
        JsonNode is = json.get("incomeStandard");
        assertThat(is.get("rirMetroPercent").asDouble()).isEqualTo(18.4);
        assertThat(is.get("rirOverallPercent").asDouble()).isEqualTo(15.8);
        assertThat(is.get("medianIncome100PercentMonthly").asLong()).isEqualTo(2_564_238L);
    }

    @Test
    void 브라우저가_loanProducts를_보내도_RecommendRequest는_받지_않는다() throws Exception {
        String body = "{\"annualIncome\":3400,\"deposit\":800,\"workLocation\":\"강남구\",\"loanProducts\":[{\"type\":\"HACK\"}]}";
        RecommendRequest parsed = mapper.readValue(body, RecommendRequest.class);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(new AiListingRequest(parsed, List.of(), null)));

        assertThat(json.get("loanProducts")).isEmpty();   // 서버가 붙인 값만 나간다
    }
}
