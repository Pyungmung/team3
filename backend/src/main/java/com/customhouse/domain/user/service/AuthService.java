package com.customhouse.domain.user.service;

import com.customhouse.domain.user.dto.LoginRequest;
import com.customhouse.domain.user.dto.PasswordResetRequest;
import com.customhouse.domain.user.dto.SignupRequest;
import com.customhouse.domain.user.dto.TokenResponse;
import com.customhouse.domain.user.dto.UserResponse;
import com.customhouse.domain.user.entity.EmailVerification;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 허겸] 회원 도메인 - 회원가입 / 로그인 / 토큰 재발급 (Spring Security + JWT)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (!emailVerificationService.isSignupEmailVerified(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim())
                .marketingConsent(request.isMarketingConsent())
                .provider("LOCAL")
                .build();

        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "리프레시 토큰이 유효하지 않거나 만료되었습니다.");
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "이미 폐기되었거나 일치하지 않는 리프레시 토큰입니다.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return TokenResponse.bearer(newAccessToken, refreshToken);
    }

    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    /** 비밀번호 재설정: 인증번호를 확인하고 통과하면 바로 비밀번호를 반영한다 (별도 재설정 토큰 없음). */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(u -> "LOCAL".equals(u.getProvider()))
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "가입된 이메일 계정을 찾을 수 없습니다."));

        emailVerificationService.verifyCode(request.email(), request.code(), EmailVerification.Purpose.PASSWORD_RESET);

        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    /** 로그인/OAuth2 성공 공통: Access/Refresh 토큰 발급 후 Refresh Token을 사용자 레코드에 저장(대조용). */
    @Transactional
    public TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());
        user.setRefreshToken(refreshToken);
        return TokenResponse.bearer(accessToken, refreshToken);
    }
}
