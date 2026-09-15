package com.customhouse.domain.user.service;

import com.customhouse.domain.user.dto.TokenResponse;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * [담당: 허겸] 회원 도메인 - 네이버 로그인 성공 후 JWT를 발급해 프론트엔드로 리다이렉트한다.
 * 프론트엔드는 순수 정적 페이지(oauth-callback.html)라 서버사이드 콜백 처리가 없으므로,
 * 토큰을 쿼리 파라미터로 실어 보내고 그 페이지의 JS가 localStorage에 저장하도록 한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${oauth2.success-redirect-url}")
    private String successRedirectUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long userId = ((Number) oAuth2User.getAttributes().get("userId")).longValue();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        TokenResponse tokens = authService.issueTokens(user);

        String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("accessToken", tokens.accessToken())
                .queryParam("refreshToken", tokens.refreshToken())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
