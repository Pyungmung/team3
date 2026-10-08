package com.customhouse.domain.housingpolicy.dto;

/**
 * [담당: 송귀성] 정책 저장/삭제 결과 - 정책 본문(삭제면 null)과, 이 정책을 관심정책으로 담아 알림을 받은 사용자 수(관리자 화면 안내용).
 */
public record HousingPolicySaveResponse(HousingPolicyResponse policy, int notifiedUsers) {
}
