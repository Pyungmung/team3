package com.customhouse.domain.user.controller;

import com.customhouse.domain.user.dto.LoginRequest;
import com.customhouse.domain.user.dto.RefreshRequest;
import com.customhouse.domain.user.dto.SignupRequest;
import com.customhouse.domain.user.dto.TokenResponse;
import com.customhouse.domain.user.dto.UserResponse;
import com.customhouse.domain.user.service.AuthService;
import com.customhouse.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 허겸] 회원 도메인 - 이메일 회원가입 / 로그인 / 토큰 재발급 API
 * 네이버 소셜 로그인은 별도 엔드포인트 없이 Spring Security OAuth2 Client가 제공하는
 * GET /oauth2/authorization/naver 로 리다이렉트하면 된다 (SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("로그인에 성공했습니다.", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("토큰이 재발급되었습니다.", response));
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.ok(authService.isEmailDuplicate(email)));
    }
}
