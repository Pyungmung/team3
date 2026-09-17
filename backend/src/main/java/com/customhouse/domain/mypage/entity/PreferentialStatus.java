package com.customhouse.domain.mypage.entity;

/**
 * [담당: 황진구] 마이페이지 도메인 - 우대사항 (다중 선택 가능)
 * 자기신고 항목이라, 청년 정책 자격 판별(customhouse-ai/app/services/policy_matcher.py의
 * required_preferential_status) 시 미입력이면 보수적으로 탈락 처리된다.
 */
public enum PreferentialStatus {
    BASIC_LIVELIHOOD,  // 기초생활수급자
    NEAR_POVERTY,      // 차상위계층
    SINGLE_PARENT,     // 한부모가족
    INDEPENDENT_YOUTH, // 자립준비청년
    NEWLYWED,          // 신혼부부
    MULTI_CHILD        // 다자녀가구
}
