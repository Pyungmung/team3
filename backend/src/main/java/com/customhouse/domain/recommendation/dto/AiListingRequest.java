package com.customhouse.domain.recommendation.dto;

import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * [담당: 송귀성] 더미 매물 추천을 AI 엔진(customhouse-ai)에 넘길 때의 요청 본문.
 * 브라우저가 보낸 조건(RecommendRequest)은 그대로 펼쳐서(@JsonUnwrapped) 보내고, 거기에 관리자 화면에서 저장한 대출 조건(loanProducts)을
 * 서버가 DB에서 읽어 덧붙인다. 대출 조건은 클라이언트 입력이 아니라서(RecommendRequest에 필드가 없다) 브라우저가 조작할 수 없다.
 */
public record AiListingRequest(
        @JsonUnwrapped RecommendRequest request,
        List<LoanProductResponse> loanProducts
) {
}
