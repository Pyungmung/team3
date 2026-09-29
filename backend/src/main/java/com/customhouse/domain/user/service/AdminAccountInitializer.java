package com.customhouse.domain.user.service;

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * [담당: 송귀성] 관리자 계정 - 서버가 켜질 때 admin.email(기본 admin@admin.com) 계정을 만들거나 관리자(ADMIN)로 승격한다.
 * 관리자 이메일은 실제 메일함이 없을 수 있어 일반 회원가입(이메일 인증)으로는 만들 수 없고, 관리자 여부는 DB의 role 값으로만 정해진다.
 *
 * - admin.password(backend/.env의 ADMIN_PASSWORD)가 비어 있으면 아무것도 하지 않는다 (계정을 만들지도, 승격하지도 않는다).
 * - 계정이 없으면 BCrypt로 암호화한 비밀번호로 만든다.
 * - 계정이 이미 있으면 ADMIN으로 승격하고 비밀번호를 ADMIN_PASSWORD 값으로 맞춘다. 개발용 시드(DevAdminSeeder)가
 *   admin@admin.com을 코드에 적힌 알려진 비밀번호로 미리 만들어 두는 경우가 있어서, 그 계정에 관리자 권한만 주면
 *   저장소를 보는 누구나 관리자로 로그인할 수 있게 된다. 그래서 승격할 때 비밀번호도 반드시 .env 값으로 바꾼다
 *   (이미 같은 값이면 다시 저장하지 않는다).
 * - 비밀번호는 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    /** 너무 짧은 비밀번호로 관리자 계정이 만들어지는 것을 막는다. */
    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@admin.com}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_PASSWORD가 설정되어 있지 않아 관리자 계정을 만들지 않습니다.");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD가 {}자 미만이라 관리자 계정을 만들지 않습니다.", MIN_PASSWORD_LENGTH);
            return;
        }

        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                existing -> {
                    boolean changed = false;
                    if (!existing.isAdmin()) {
                        existing.setRole(User.ROLE_ADMIN);
                        changed = true;
                    }
                    if (existing.getPassword() == null || !passwordEncoder.matches(adminPassword, existing.getPassword())) {
                        existing.setPassword(passwordEncoder.encode(adminPassword));
                        changed = true;
                    }
                    if (changed) {
                        userRepository.save(existing);
                        log.info("기존 계정 {}을(를) 관리자로 승격하고 비밀번호를 ADMIN_PASSWORD 값으로 맞췄습니다.", adminEmail);
                    }
                },
                () -> {
                    userRepository.save(User.builder()
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminPassword))
                            .nickname("관리자")
                            .provider("LOCAL")
                            .role(User.ROLE_ADMIN)
                            .build());
                    log.info("관리자 계정 {}을(를) 만들었습니다.", adminEmail);
                });
    }
}
