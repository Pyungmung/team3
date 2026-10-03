package com.customhouse.domain.mypage.entity;

/**
 * [담당: 황진구] 마이페이지 도메인 - 우대사항 (다중 선택 가능)
 * 자기신고 항목이라, 청년 정책 자격 판별(customhouse-ai/app/services/policy_matcher.py의
 * required_preferential_status) 시 미입력이면 보수적으로 탈락 처리된다.
 * 전세자금대출 우대사항(domain.loan.entity.LoanPreferenceKey)과 코드명이 같아야 한다 - AI 엔진
 * (loan_matcher.py의 _SELF_REPORTED_PREFERENCES)이 이 코드를 그대로 대출 우대사항 판별에도 쓴다.
 */
public enum PreferentialStatus {
    BASIC_LIVELIHOOD,  // 기초생활수급자
    NEAR_POVERTY,      // 차상위계층
    SINGLE_PARENT,     // 한부모가구
    INDEPENDENT_YOUTH, // 자립준비청년
    NEWLYWED,          // 신혼부부(기혼자포함)
    DUAL_INCOME,       // 맞벌이부부 - 아래 셋 + MULTI_CHILD는 화면에서 "부가 우대사항"으로 시각적으로만 묶인, 각각 독립된 우대사항
    ONE_CHILD,         // 1자녀
    TWO_CHILDREN,      // 2자녀
    MULTI_CHILD,       // 다자녀가구
    DISABLED,          // 장애인
    MULTICULTURAL,     // 다문화가구
    ELDERLY_DEPENDENT, // 노인부양가구
    ELDERLY_HOUSEHOLD  // 고령자가구
}
