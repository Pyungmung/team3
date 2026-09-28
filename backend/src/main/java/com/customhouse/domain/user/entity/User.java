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

    /** 휴대폰 번호 (010-0000-0000 형식, 마이페이지에서 선택 입력. 없으면 null) */
    @Column(length = 20)
    private String phone;

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

    // 광고·마케팅 목적 개인정보 수집·이용 동의 (선택). 회원가입 시 체크박스로 최초 설정되고,
    // 마이페이지 약관 탭에서 언제든 다시 켜고 끌 수 있다. 기본값 false(동의 안 함).
    // 실제 광고 노출 로직은 아직 없고 값만 저장해둔다 (2026-09-22).
    @Builder.Default
    @Column(nullable = false)
    private boolean marketingConsent = false;
}
