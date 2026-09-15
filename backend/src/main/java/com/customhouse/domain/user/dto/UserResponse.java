package com.customhouse.domain.user.dto;

import com.customhouse.domain.user.entity.User;

/**
 * [담당: 허겸] 회원 도메인 - 클라이언트에 노출하는 사용자 정보 (비밀번호/토큰 등 민감정보 제외)
 */
public record UserResponse(Long id, String email, String nickname, String provider) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProvider());
    }
}
