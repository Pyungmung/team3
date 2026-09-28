package com.customhouse.global.config;

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * [담당: 허겸] 공통 인프라 - 개발/테스트용 상시 admin 계정 시드.
 * 기본 dev 프로필은 H2 인메모리라 서버를 재시작하면 회원이 전부 사라지므로, 기동할 때마다
 * admin 계정이 없으면 만들어 준다 (이미 있으면 건드리지 않아서 MySQL처럼 데이터가 남는 환경에서도 안전).
 *
 * 로그인이 이메일 형식만 받아서(LoginRequest @Email) 아이디는 admin@admin.com 이다.
 * 이 프로젝트에는 아직 권한(role) 개념이 없어서 일반 회원과 권한 차이는 없다.
 *
 * 주의: 비밀번호가 코드에 그대로 적혀 있으므로 운영(prod) 프로필에서는 절대 만들지 않는다 (@Profile("!prod")).
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DevAdminSeeder implements ApplicationRunner {

    static final String ADMIN_EMAIL = "admin@admin.com";
    static final String ADMIN_PASSWORD = "qwer1234";
    static final String ADMIN_NICKNAME = "admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .nickname(ADMIN_NICKNAME)
                .provider("LOCAL")
                .build());
        log.info("개발용 admin 계정을 생성했습니다: {}", ADMIN_EMAIL);
    }
}
