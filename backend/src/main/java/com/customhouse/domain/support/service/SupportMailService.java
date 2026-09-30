package com.customhouse.domain.support.service;

import com.customhouse.domain.listing.dto.ListingReportRequest;
import com.customhouse.domain.support.dto.InquiryRequest;
import com.customhouse.domain.support.dto.PolicyCorrectionRequest;
import com.customhouse.global.mail.MailClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * [담당: 미정] 고객센터 - 이메일 문의를 관리자 메일함(support.receiver-email)으로 전달한다.
 * 실제 발송은 global/mail/MailClient(SendGrid HTTP API)가 한다 - backend/.env의
 * SENDGRID_API_KEY/SENDGRID_FROM_EMAIL이 필요하다. 값이 비어 있으면 발송 시 MAIL_SEND_FAILED
 * 예외가 발생한다 (.env.example 참고).
 */
@Service
@RequiredArgsConstructor
public class SupportMailService {

    private final MailClient mailClient;

    @Value("${support.receiver-email}")
    private String receiverEmail;

    /** 정정 유형 코드 -> 메일에 쓰는 한글 문구 (제목에 클라이언트 문구가 그대로 들어가지 않게 서버가 정한다). */
    private static final Map<String, String> CORRECTION_TYPE_LABELS = Map.of(
            "INFO_ERROR", "지원 대상/금액 등 정보 오류",
            "EXPIRED", "종료되었거나 변경된 정책",
            "MISSING", "누락된 정책 제보",
            "OTHER", "기타");

    /** 허위매물 신고 유형 코드 -> 메일에 쓰는 한글 문구. */
    private static final Map<String, String> LISTING_REPORT_TYPE_LABELS = Map.of(
            "FAKE_PRICE", "가격이 실제와 다름",
            "ALREADY_SOLD", "이미 계약된 매물",
            "WRONG_INFO", "면적/층/옵션 등 정보 불일치",
            "PHOTO_MISMATCH", "사진이 실제와 다름",
            "OTHER", "기타");

    /** 메일 제목/본문 한 줄에 넣을 문자열에서 줄바꿈·탭을 없애고 길이를 자른다 (메일 헤더 주입 방지). */
    private static String oneLine(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("[\r\n\t]+", " ").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) + "…" : cleaned;
    }

    /** 리포트의 "정책정보 정정신고" 팝업에서 온 정정 요청을 관리자 메일함으로 전달한다. */
    public void sendPolicyCorrection(PolicyCorrectionRequest request) {
        String typeLabel = CORRECTION_TYPE_LABELS.getOrDefault(request.correctionType(), "기타");
        String policyName = oneLine(request.policyName(), 60);

        String subject = "[맞집 정책정보 정정신고] " + typeLabel + (policyName.isEmpty() ? "" : " - " + policyName);
        String text = """
                신고자: %s <%s>
                정정 유형: %s

                [대상 정책]
                정책 ID: %s
                정책명: %s
                기관: %s
                지역: %s
                현재 표시된 내용: %s

                [정정할 내용]
                %s
                """.formatted(
                oneLine(request.name(), 50), request.email(), typeLabel,
                oneLine(request.policyId(), 40), policyName, oneLine(request.policyAgency(), 100),
                oneLine(request.policyRegion(), 30), oneLine(request.currentDescription(), 500),
                request.message());

        mailClient.send(receiverEmail, subject, text, request.email());
    }

    /**
     * 리포트 추천 매물 카드의 "허위매물 신고" 팝업에서 온 신고를 관리자 메일함으로 전달한다.
     * 신고자 이름/이메일은 로그인 회원 정보(서버가 확인한 값)이고, replyTo는 신고자가 답장받을 주소다.
     */
    public void sendListingReport(String reporterName, String replyToEmail, String listingId,
                                  ListingReportRequest request, long totalReports) {
        String typeLabel = LISTING_REPORT_TYPE_LABELS.getOrDefault(request.reportType(), "기타");

        String subject = "[맞집 허위매물 신고] " + typeLabel + " - " + oneLine(listingId, 40);
        String text = """
                신고자: %s <%s>
                신고 유형: %s
                이 매물의 누적 신고: %d건

                [대상 매물]
                매물번호: %s
                주소: %s
                거래: %s / 보증금 %s만원 / 월세 %s만원

                [신고 내용]
                %s
                """.formatted(
                oneLine(reporterName, 50), replyToEmail, typeLabel, totalReports,
                oneLine(listingId, 40), oneLine(request.address(), 300),
                oneLine(request.leaseType(), 10),
                request.deposit() == null ? "-" : request.deposit(),
                request.monthlyRent() == null ? "-" : request.monthlyRent(),
                request.message());

        mailClient.send(receiverEmail, subject, text, replyToEmail);
    }

    public void sendInquiry(InquiryRequest request) {
        String subject = "[맞집 고객센터 문의] " + request.name();
        String text = """
                문의자: %s <%s>

                %s
                """.formatted(request.name(), request.email(), request.message());

        mailClient.send(receiverEmail, subject, text, request.email());
    }
}
