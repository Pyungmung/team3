package com.customhouse.global.jwt;

/**
 * [담당: 허겸] 공통 인프라 - JWT 검증 후 SecurityContext에 올라가는 인증 주체.
 * 컨트롤러에서 @AuthenticationPrincipal AuthenticatedUser principal 형태로 바로 받을 수 있다.
 */
public record AuthenticatedUser(Long id, String email) {
}
