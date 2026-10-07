package com.customhouse.domain.recommendation.dto;

// [담당: 송귀성] 진단 요청의 나이 범위(0~120) 검증 - 범위를 벗어나면 AI 엔진까지 가지 않고 백엔드에서 400으로 거절한다 (2026-10-07).

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private RecommendRequest withAge(Integer age) {
        return new RecommendRequest(3400, null, 800, null, null, "강남구", null, null, 40, age, true, null, null, null, List.of(),
                null, null, null, "PUBLIC", true, List.of(), null, null);
    }

    @Test
    void 나이_0과_120과_비움은_통과한다() {
        assertThat(validator.validate(withAge(0))).isEmpty();
        assertThat(validator.validate(withAge(120))).isEmpty();
        assertThat(validator.validate(withAge(null))).isEmpty();
    }

    @Test
    void 나이가_음수이거나_120을_넘으면_거절한다() {
        assertThat(validator.validate(withAge(-1))).extracting(v -> v.getPropertyPath().toString()).containsExactly("age");
        assertThat(validator.validate(withAge(121))).extracting(v -> v.getPropertyPath().toString()).containsExactly("age");
        assertThat(validator.validate(withAge(200))).extracting(v -> v.getMessage()).containsExactly("나이는 120 이하여야 합니다.");
    }
}
