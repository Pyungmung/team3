package com.customhouse.domain.recommendation.dto;

import com.customhouse.domain.housingpolicy.dto.HousingPolicyForEngine;
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
                List.of("NEWLYWED"), null, null, "EXPLORING", "PUBLIC", true, List.of("아파트"), true, false);
    }

    @Test
    void 사용자_조건은_최상위로_펼쳐지고_대출_조건이_함께_실린다() throws Exception {
        LoanProductResponse loan = new LoanProductResponse("GENERAL_BEOTIMMOK", "일반 버팀목 전세대출", "전세", true,
                19, 34, 5000, 6000, 33700, 20000, 85.0, 80.0, 20000, null, null, null, null, null,
                Map.of("NEWLYWED", new LoanPreference(true, 0.2, null, null, null, null, null, null)), null, null, null);

        HousingPolicyForEngine policy = new HousingPolicyForEngine(7L, "강남구", "서울시", "청년월세지원", "월 20만원", 19, 39, 5000, 34500, 150,
                false, true, false, true, false, "https://www.seoul.go.kr/a");
        IncomeStandardResponse incomeStandard = new IncomeStandardResponse(15.8, 18.4, 18.3, 16.2, 19.4, 2024, "국토교통부,「주거실태조사」", 2_564_238L, null);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new AiListingRequest(recommend(), List.of(loan), incomeStandard, new com.customhouse.domain.appsetting.dto.AppSettingResponse(500, null), List.of(policy))));

        assertThat(json.get("appSettings").get("recommendationLimit").asInt()).isEqualTo(500);
        assertThat(json.get("showAllDeposits").asBoolean()).isTrue();      // 리포트 체크박스 값이 AI 엔진 요청에 그대로 실린다
        assertThat(json.get("includeSemiJeonse").asBoolean()).isFalse();   // 기타 설정도 함께 실린다

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
        // 주거지원정책 (AI 엔진 HousingPolicyCondition 필드 이름과 같아야 한다)
        JsonNode p = json.get("housingPolicies").get(0);
        assertThat(p.get("id").asLong()).isEqualTo(7L);
        assertThat(p.get("region").asText()).isEqualTo("강남구");
        assertThat(p.get("name").asText()).isEqualTo("청년월세지원");
        assertThat(p.get("minAge").asInt()).isEqualTo(19);
        assertThat(p.get("maxAnnualIncome").asInt()).isEqualTo(5000);
        assertThat(p.get("maxAsset").asInt()).isEqualTo(34500);
        assertThat(p.get("medianIncomePercent").asInt()).isEqualTo(150);
        assertThat(p.get("requireSme").asBoolean()).isTrue();
        assertThat(p.get("requireNoHousehold").asBoolean()).isTrue();
        assertThat(p.get("requireNewlywed").asBoolean()).isFalse();
        assertThat(p.get("loan").asBoolean()).isFalse();
        assertThat(p.get("link").asText()).isEqualTo("https://www.seoul.go.kr/a");
        assertThat(p.has("note")).isFalse();   // 관리자용 메모는 AI 엔진에 보내지 않는다
    }

    @Test
    void 브라우저가_loanProducts를_보내도_RecommendRequest는_받지_않는다() throws Exception {
        String body = "{\"annualIncome\":3400,\"deposit\":800,\"workLocation\":\"강남구\",\"loanProducts\":[{\"type\":\"HACK\"}],\"housingPolicies\":[{\"id\":1}]}";
        RecommendRequest parsed = mapper.readValue(body, RecommendRequest.class);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(new AiListingRequest(parsed, List.of(), null, null, List.of())));

        assertThat(json.get("loanProducts")).isEmpty();   // 서버가 붙인 값만 나간다
        assertThat(json.get("housingPolicies")).isEmpty();
    }
}
