package com.customhouse.domain.loan.dto;

import java.util.List;

/**
 * [담당: 송귀성] 관리자 화면 전체 응답: 화면이 그릴 우대사항 목록(코드+한글명, 표시 순서)과 대출 5종의 현재 조건.
 */
public record LoanListResponse(List<PreferenceInfo> preferenceOptions, List<LoanProductResponse> loans) {

    public record PreferenceInfo(String key, String label) {
    }
}
