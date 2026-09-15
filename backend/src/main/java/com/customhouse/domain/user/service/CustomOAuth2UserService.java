package com.customhouse.domain.user.service;

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [담당: 허겸] 회원 도메인 - 네이버 OAuth2 로그인 사용자 정보 매핑
 * 네이버 응답은 { resultcode, message, response: { id, email, nickname, ... } } 형태라
 * 표준 DefaultOAuth2UserService만으로는 사용자 속성을 바로 못 읽는다 (response로 한 번 더 감싸져 있음).
 * application.yml의 user-name-attribute: response 설정과 짝을 이룬다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PROVIDER_NAVER = "NAVER";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!"naver".equals(registrationId)) {
            throw new OAuth2AuthenticationException("지원하지 않는 로그인 제공자입니다: " + registrationId);
        }

        Object responseObj = oAuth2User.getAttributes().get("response");
        if (!(responseObj instanceof Map<?, ?> rawAccount)) {
            throw new OAuth2AuthenticationException("네이버 응답 형식이 올바르지 않습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> naverAccount = (Map<String, Object>) rawAccount;

        String providerId = String.valueOf(naverAccount.get("id"));
        String email = (String) naverAccount.get("email");
        String nickname = (String) naverAccount.getOrDefault("nickname", "네이버사용자");

        User user = userRepository.findByProviderAndProviderId(PROVIDER_NAVER, providerId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(email)
                                .nickname(nickname)
                                .provider(PROVIDER_NAVER)
                                .providerId(providerId)
                                .build()
                ));

        Map<String, Object> customAttributes = new HashMap<>(naverAccount);
        customAttributes.put("userId", user.getId());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                customAttributes,
                "id"
        );
    }
}
