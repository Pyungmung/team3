package com.customhouse.domain.housingpolicy.dto;

// [담당: 송귀성] 주거지원정책 저장 요청의 입력 검증 - 지역은 서울(공통)+25개 자치구만, 필수 칸, 나이/금액 범위, 링크는 http(s)만 (2026-10-08).

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HousingPolicyRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalid(String region, String name, Integer minAge, Integer maxAge, Integer income, Integer pct, String link) {
        Set<ConstraintViolation<HousingPolicyRequest>> v = validator.validate(new HousingPolicyRequest(region, "서울시", name, "지원 내용",
                minAge, maxAge, income, 34500, pct, false, false, false, false, false, null, link));
        return v.stream().map(x -> x.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void 정상_요청과_비워둔_조건은_통과한다() {
        assertThat(invalid("강남구", "청년월세지원", 19, 39, 5000, 150, "https://www.seoul.go.kr/a")).isEmpty();
        assertThat(invalid("서울", "청년월세지원", null, null, null, null, null)).isEmpty();
        assertThat(invalid("중랑구", "청년월세지원", 19, 19, 0, 0, "")).isEmpty();
    }

    @Test
    void 서울과_25개_자치구_밖의_지역은_거절한다() {
        assertThat(invalid("부산", "정책", null, null, null, null, null)).containsExactly("region");
        assertThat(invalid("강남", "정책", null, null, null, null, null)).containsExactly("region");
        assertThat(invalid("", "정책", null, null, null, null, null)).contains("region");
    }

    @Test
    void 필수_칸이_비면_거절한다() {
        assertThat(invalid("서울", " ", null, null, null, null, null)).containsExactly("name");
    }

    @Test
    void 나이와_금액의_상식_밖_값과_뒤집힌_나이_범위는_거절한다() {
        assertThat(invalid("서울", "정책", -1, null, null, null, null)).containsExactly("minAge");
        assertThat(invalid("서울", "정책", null, 121, null, null, null)).containsExactly("maxAge");
        assertThat(invalid("서울", "정책", 40, 30, null, null, null)).containsExactly("ageRangeValid");
        assertThat(invalid("서울", "정책", null, null, -5, null, null)).containsExactly("maxAnnualIncome");
        assertThat(invalid("서울", "정책", null, null, 1_000_001, null, null)).containsExactly("maxAnnualIncome");
        assertThat(invalid("서울", "정책", null, null, null, 1001, null)).containsExactly("medianIncomePercent");
    }

    @Test
    void http_https가_아닌_링크는_거절한다() {
        assertThat(invalid("서울", "정책", null, null, null, null, "javascript:alert(1)")).containsExactly("link");
        assertThat(invalid("서울", "정책", null, null, null, null, "www.example.go.kr")).containsExactly("link");
        assertThat(invalid("서울", "정책", null, null, null, null, "https://a.kr/ 공백")).containsExactly("link");
    }
}
