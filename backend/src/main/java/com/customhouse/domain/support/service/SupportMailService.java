package com.customhouse.domain.support.service;

import com.customhouse.domain.support.dto.InquiryRequest;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * [담당: 미정] 고객센터 - 이메일 문의를 관리자 메일함(support.receiver-email)으로 전달한다.
 * 실제 발송에는 backend/.env의 MAIL_USERNAME/MAIL_APP_PASSWORD(Gmail 앱 비밀번호)가 필요하다.
 * 값이 비어 있으면 발송 시 MAIL_SEND_FAILED 예외가 발생한다 (.env.example 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportMailService {

    private final JavaMailSender mailSender;

    @Value("${support.receiver-email}")
    private String receiverEmail;

    // Gmail은 From 헤더가 없거나 인증 계정과 다르면 조용히 발신을 거부/드롭한다 (에러 없이 안 옴).
    // 그래서 From을 SMTP 인증 계정(spring.mail.username)으로 명시한다.
    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendInquiry(InquiryRequest request) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(senderEmail);
        mail.setTo(receiverEmail);
        mail.setReplyTo(request.email());
        mail.setSubject("[맞집 고객센터 문의] " + request.name());
        mail.setText("""
                문의자: %s <%s>

                %s
                """.formatted(request.name(), request.email(), request.message()));

        try {
            mailSender.send(mail);
        } catch (MailException e) {
            log.error("고객센터 문의 메일 발송 실패 (문의자: {})", request.email(), e);
            throw new CustomException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
