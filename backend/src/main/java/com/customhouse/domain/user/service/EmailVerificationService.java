package com.customhouse.domain.user.service;

import com.customhouse.domain.user.entity.EmailVerification;
import com.customhouse.domain.user.repository.EmailVerificationRepository;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.mail.MailClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * [담당: 허겸] 회원 도메인 - 이메일 인증번호 발송/확인 (회원가입 이메일 인증, 비밀번호 재설정 공용)
 * SupportMailService와 같은 방식(global/mail/MailClient, SendGrid HTTP API)으로 메일을 보낸다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final long CODE_TTL_MINUTES = 5;
    // 회원가입은 "인증 확인" 후 나머지 폼을 마저 채우고 제출하기까지 시간이 걸릴 수 있어,
    // 인증번호 자체의 유효시간(5분)과 별개로 "인증 완료 상태"는 더 길게(30분) 유지한다.
    private static final long SIGNUP_VERIFIED_VALID_MINUTES = 30;

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final MailClient mailClient;

    @Transactional
    public void sendSignupCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        issueAndSendCode(email, EmailVerification.Purpose.SIGNUP, "[맞집] 회원가입 이메일 인증번호");
    }

    @Transactional
    public void sendPasswordResetCode(String email) {
        userRepository.findByEmail(email)
                .filter(user -> "LOCAL".equals(user.getProvider()))
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "가입된 이메일 계정을 찾을 수 없습니다."));
        issueAndSendCode(email, EmailVerification.Purpose.PASSWORD_RESET, "[맞집] 비밀번호 재설정 인증번호");
    }

    private void issueAndSendCode(String email, EmailVerification.Purpose purpose, String subject) {
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        emailVerificationRepository.save(
                EmailVerification.builder()
                        .email(email)
                        .code(code)
                        .purpose(purpose)
                        .expiresAt(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES))
                        .build()
        );

        String text = "인증번호는 [%s] 입니다.\n%d분 안에 입력해주세요.".formatted(code, CODE_TTL_MINUTES);
        mailClient.send(email, subject, text, null);
    }

    /** 인증번호를 확인하고, 맞으면 verified 처리한다. 비밀번호 재설정은 이 호출 직후 바로 비밀번호를 반영한다. */
    @Transactional
    public void verifyCode(String email, String code, EmailVerification.Purpose purpose) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_CODE));

        if (verification.isExpired()) {
            throw new CustomException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!verification.getCode().equals(code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        verification.setVerified(true);
    }

    /** 회원가입 완료 직전, 이 이메일이 최근에 인증됐는지 확인한다 (AuthService.signup에서 호출). */
    public boolean isSignupEmailVerified(String email) {
        return emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, EmailVerification.Purpose.SIGNUP)
                .filter(EmailVerification::isVerified)
                .filter(v -> v.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(SIGNUP_VERIFIED_VALID_MINUTES)))
                .isPresent();
    }
}
