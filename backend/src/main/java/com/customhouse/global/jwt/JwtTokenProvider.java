package com.customhouse.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * [담당: 허겸] 공통 인프라 - JWT Access/Refresh 토큰 발급 및 검증.
 * jwt.secret / jwt.access-token-expire-ms / jwt.refresh-token-expire-ms 설정은 application.yml 참고.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";

    /**
     * .env에 JWT_SECRET= 처럼 "키는 있는데 값이 빈" 줄이 있으면, spring.config.import는
     * 그 프로퍼티가 "존재한다"고 보고 application.yml의 ${JWT_SECRET:기본값} 기본값 대신
     * 빈 문자열을 그대로 넘겨버린다 (플레이스홀더는 "미설정"과 "빈 값"을 구분하지 않음).
     * 그러면 HMAC 키 생성이 "0 bits" 에러로 죽으므로, 여기서 한 번 더 방어한다.
     */
    private static final String FALLBACK_SECRET = "dev-only-temporary-jwt-secret-key-please-change-32bytes-min";

    private final SecretKey key;
    private final long accessTokenExpireMs;
    private final long refreshTokenExpireMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire-ms}") long accessTokenExpireMs,
            @Value("${jwt.refresh-token-expire-ms}") long refreshTokenExpireMs
    ) {
        String effectiveSecret = (secret == null || secret.isBlank()) ? FALLBACK_SECRET : secret;
        this.key = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireMs = accessTokenExpireMs;
        this.refreshTokenExpireMs = refreshTokenExpireMs;
    }

    public String generateAccessToken(Long userId, String email) {
        return buildToken(userId, email, "ACCESS", accessTokenExpireMs);
    }

    public String generateRefreshToken(Long userId, String email) {
        return buildToken(userId, email, "REFRESH", refreshTokenExpireMs);
    }

    private String buildToken(Long userId, String email, String type, long expireMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getEmail(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
