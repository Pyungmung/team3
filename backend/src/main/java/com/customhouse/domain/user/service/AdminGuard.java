package com.customhouse.domain.user.service;

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [담당: 송귀성] 관리자 전용 API의 2차 확인. SecurityConfig가 토큰의 role 클레임으로 /api/admin/** 를 1차로 막고,
 * 여기서 DB의 현재 role을 다시 확인한다 (관리자 권한을 뺀 뒤에도 이미 발급된 토큰으로 접근이 남는 것을 막는다).
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final UserRepository userRepository;

    public void requireAdmin(Long userId) {
        boolean admin = userId != null && userRepository.findById(userId).map(User::isAdmin).orElse(false);
        if (!admin) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
