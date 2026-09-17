package com.customhouse.domain.mypage.entity;

/**
 * [담당: 황진구] 마이페이지 도메인 - 직업종류
 * 일부 청년 정책(중소기업 취업청년 전월세보증금대출 등)이 직업종류를 자격 조건으로 본다
 * (customhouse-ai/app/services/policy_matcher.py의 eligible_job_types와 매칭).
 */
public enum JobType {
    GOVERNMENT, // 공무원
    SME,        // 중소기업
    MID_SIZED,  // 중견기업
    LARGE_CORP  // 대기업
}
