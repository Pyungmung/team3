package com.customhouse.domain.user.dto;

/**
 * [담당: 허겸] 회원 도메인 - 로그인/재발급 성공 시 내려주는 토큰 쌍.
 * 프론트엔드는 이 값을 localStorage에 저장하고 Authorization: Bearer {accessToken} 헤더로 전송한다 (CLAUDE.md 컨벤션).
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType) {

    public static TokenResponse bearer(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer");
    }
}
