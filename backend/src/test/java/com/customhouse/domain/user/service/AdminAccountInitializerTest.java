package com.customhouse.domain.user.service;

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 관리자 계정 자동 생성/승격 테스트. 저장소와 암호화기는 목으로 바꾼다.
 */
class AdminAccountInitializerTest {

    private static final String EMAIL = "admin@admin.com";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        initializer = new AdminAccountInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "adminEmail", EMAIL);
    }

    private void withPassword(String password) {
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
    }

    @Test
    void 비밀번호가_비어_있으면_아무것도_하지_않는다() {
        withPassword("");

        initializer.run(null);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void 비밀번호가_너무_짧으면_계정을_만들지_않는다() {
        withPassword("short");

        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void 계정이_없으면_암호화한_비밀번호로_관리자_계정을_만든다() {
        withPassword("a-long-enough-password");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getPassword()).isEqualTo("ENCODED");   // 평문이 아니라 암호화한 값만 저장한다
        assertThat(saved.isAdmin()).isTrue();
        assertThat(saved.getProvider()).isEqualTo("LOCAL");
    }

    @Test
    void 이미_있는_계정은_승격하면서_비밀번호를_ENV_값으로_바꾼다() {
        // 개발용 시드(DevAdminSeeder)가 코드에 적힌 알려진 비밀번호로 미리 만든 계정을 그대로 관리자로 만들면 안 된다
        withPassword("a-long-enough-password");
        User existing = User.builder().email(EMAIL).password("KNOWN-OLD-HASH").nickname("기존").build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("a-long-enough-password", "KNOWN-OLD-HASH")).thenReturn(false);

        initializer.run(null);

        assertThat(existing.isAdmin()).isTrue();
        assertThat(existing.getPassword()).isEqualTo("ENCODED");
        verify(userRepository).save(existing);
    }

    @Test
    void 이미_관리자이고_비밀번호도_같으면_다시_저장하지_않는다() {
        withPassword("a-long-enough-password");
        User admin = User.builder().email(EMAIL).password("HASH").nickname("관리자").role(User.ROLE_ADMIN).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("a-long-enough-password", "HASH")).thenReturn(true);

        initializer.run(null);

        verify(userRepository, never()).save(any());
    }
}
