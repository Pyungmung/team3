package com.customhouse.domain.user.controller;

import com.customhouse.domain.user.dto.UserResponse;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 허겸] 회원 도메인 - 로그인한 내 정보 조회.
 * JWT 인증이 실제로 동작하는지 확인하는 용도의 보호된(authenticated) 엔드포인트 (SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }
}
