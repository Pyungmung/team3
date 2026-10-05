package com.customhouse.domain.recommendation.dto;

import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.loan.dto.LoanProductResponse;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * [담당: 송귀성] AI 엔진(customhouse-ai)의 진단(/api/v1/diagnosis, /api/v1/diagnosis/listings) 요청 본문.
 * 브라우저가 보낸 조건(RecommendRequest)은 그대로 펼쳐서(@JsonUnwrapped) 보내고, 거기에 관리자 화면에서 저장한
 * 대출 조건(loanProducts)과 기준소득 통계(incomeStandard, RIR·기준중위소득)를 서버가 DB에서 읽어 덧붙인다.
 * 둘 다 클라이언트 입력이 아니라서(RecommendRequest에 필드가 없다) 브라우저가 조작할 수 없다.
 * 관리자 화면(기타 설정)에서 저장한 appSettings(추천 개수 상한)도 같은 방식으로 덧붙는다.
 * 대출 매칭이 없는 /api/v1/diagnosis 호출은 loanProducts를 빈 목록으로 보낸다(AiEngineClient 참고).
 */
public record AiListingRequest(
        @JsonUnwrapped RecommendRequest request,
        List<LoanProductResponse> loanProducts,
        IncomeStandardResponse incomeStandard,
        AppSettingResponse appSettings
) {
}
