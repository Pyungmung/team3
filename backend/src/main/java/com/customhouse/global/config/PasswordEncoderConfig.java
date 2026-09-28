package com.customhouse.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * [담당: 허겸] 공통 인프라 - PasswordEncoder를 SecurityConfig에서 분리한 이유:
 * SecurityConfig(OAuth2SuccessHandler 의존) → AuthService(PasswordEncoder 의존) → SecurityConfig
 * 로 순환 참조가 생기기 때문에, 다른 빈에 의존하지 않는 별도 설정 클래스로 뺐다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
