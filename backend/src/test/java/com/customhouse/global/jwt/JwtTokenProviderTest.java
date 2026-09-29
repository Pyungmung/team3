package com.customhouse.global.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [담당: 송귀성] JWT의 role 클레임 테스트. 권한이 토큰에 서명되어 들어가고, 권한 클레임이 없던 예전 토큰은 USER로 읽히는지 확인한다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-role-claim-needs-32-bytes-min";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600_000L, 1_209_600_000L);

    @Test
    void 관리자_권한이_토큰에_들어가고_읽힌다() {
        String token = provider.generateAccessToken(1L, "admin@admin.com", "ADMIN");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getRole(token)).isEqualTo("ADMIN");
        assertThat(provider.getEmail(token)).isEqualTo("admin@admin.com");
        assertThat(provider.getUserId(token)).isEqualTo(1L);
    }

    @Test
    void 권한이_비어_있으면_USER로_발급된다() {
        assertThat(provider.getRole(provider.generateAccessToken(2L, "a@a.com", null))).isEqualTo("USER");
        assertThat(provider.getRole(provider.generateRefreshToken(2L, "a@a.com", " "))).isEqualTo("USER");
    }

    @Test
    void 권한_클레임이_없는_예전_토큰은_USER로_읽힌다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String legacy = Jwts.builder()
                .subject("3")
                .claim("email", "old@old.com")
                .claim("type", "ACCESS")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(provider.validateToken(legacy)).isTrue();
        assertThat(provider.getRole(legacy)).isEqualTo("USER");
    }
}
