package com.customhouse.global.mail;

import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [담당: 허겸] 공통 인프라 - SendGrid HTTP API로 이메일을 보낸다 (SupportMailService, EmailVerificationService가 사용).
 *
 * 2026-09-30: Gmail SMTP(smtp.gmail.com:587/465)가 Render 배포에서 연결 자체가 타임아웃났다
 * (MailConnectException, 로컬은 정상 - 클라우드 호스팅이 스팸 방지로 아웃바운드 SMTP를 막는 흔한
 * 정책). SMTP 프로토콜 자체를 안 쓰는 HTTP API(포트 443, 다른 외부 API 호출과 동일)로 교체했다.
 *
 * SENDGRID_FROM_EMAIL은 SendGrid의 "Single Sender Verification"으로 인증한 이메일 주소 하나만
 * 쓸 수 있다 - 도메인 전체를 인증하는 "Domain Authentication"과 달리 DNS 레코드 없이 그 주소로 온
 * 확인 메일의 링크만 클릭하면 된다(팀에 소유한 도메인이 없어서 이 방식을 쓴다). 발급 방법:
 * SendGrid 가입 → Settings > Sender Authentication > Single Sender Verification → 이메일 인증
 * → Settings > API Keys에서 키 발급(Mail Send 권한) → SENDGRID_API_KEY로 저장.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailClient {

    private final RestClient sendgridRestClient;

    @Value("${sendgrid.api-key:}")
    private String apiKey;

    @Value("${sendgrid.from-email:}")
    private String fromEmail;

    /** 관리자 계정의 로그인용 이메일 (admin.email, 기본 admin@admin.com). 실제 메일함이 없는 주소일 수 있다. */
    @Value("${admin.email:admin@admin.com}")
    private String adminLoginEmail;

    /**
     * 관리자 계정에게 가는 메일을 실제로 받을 주소 (admin.mail-email = ADMIN_MAIL_EMAIL, 2026-10-05).
     * 관리자 계정의 이메일(admin@admin.com)은 로그인 아이디일 뿐 받을 수 있는 메일함이 아니라서, 그 주소로 가는 메일
     * (관심매물 알림, 인증/재설정 메일 등)을 이 주소로 대신 보낸다. 비워두면 바꾸지 않는다. 계정 이메일 자체는 바뀌지 않는다.
     */
    @Value("${admin.mail-email:}")
    private String adminMailEmail;

    /** 받는 사람이 관리자 로그인 이메일이면 실제 수신 주소로 바꾼다. 그 외 주소는 그대로. */
    String resolveRecipient(String to) {
        if (to != null && adminMailEmail != null && !adminMailEmail.isBlank()
                && adminLoginEmail != null && to.trim().equalsIgnoreCase(adminLoginEmail.trim())) {
            return adminMailEmail.trim();
        }
        return to;
    }

    /** replyTo는 선택(null 가능) - 문의/신고 메일처럼 "답장하면 신고자에게 바로 가게" 하고 싶을 때만 넣는다. */
    public void send(String to, String subject, String text, String replyTo) {
        to = resolveRecipient(to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))));
        body.put("from", Map.of("email", fromEmail, "name", "맞집"));
        body.put("subject", subject);
        body.put("content", List.of(Map.of("type", "text/plain", "value", text)));
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", Map.of("email", replyTo));
        }

        try {
            sendgridRestClient.post()
                    .uri("/v3/mail/send")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("이메일 발송 실패 (수신자: {})", to, e);
            throw new CustomException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
