package com.customhouse.domain.user.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [담당: 허겸] 회원 도메인 - User 엔티티
 * Spring Security + JWT 기반 인증/인가, 네이버 OAuth2 연동 대상.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 191)
    private String email;

    /** BCrypt로 암호화되어 저장된다. 소셜(네이버) 전용 계정은 null일 수 있다. */
    @Column(length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** LOCAL(이메일 가입) / NAVER */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String provider = "LOCAL";

    /** 소셜 로그인 제공자가 내려주는 고유 식별자 (LOCAL 계정은 null) */
    @Column(length = 100)
    private String providerId;

    /** 로그인 시 발급된 Refresh Token (재발급 시 대조용으로 저장) */
    @Column(length = 500)
    private String refreshToken;
}
