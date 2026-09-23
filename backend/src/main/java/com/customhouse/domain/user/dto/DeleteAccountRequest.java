package com.customhouse.domain.user.dto;

/**
 * [담당: 허겸] 회원 도메인 - 회원 탈퇴(계정 삭제) 요청.
 * password는 이메일 가입(LOCAL) 계정만 필요하다 - 소셜(NAVER) 계정은 비밀번호가 없어 검증하지 않는다.
 */
public record DeleteAccountRequest(String password) {
}
