package com.customhouse.domain.loan.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 전세자금대출 "참고 확인 페이지" 주소 저장 요청. 계산 로직과 무관하게 링크만 보관한다.
 * 비워서 보내면(null/공백) 저장된 주소를 지운다.
 */
public record LoanReferenceUrlRequest(
        @Size(max = 500, message = "주소가 너무 깁니다.")
        @Pattern(regexp = "^$|^https?://.+", message = "http:// 또는 https://로 시작하는 주소를 입력해주세요.")
        String referenceUrl
) {
}
