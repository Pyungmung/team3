package com.customhouse.domain.support.service;

import com.customhouse.domain.listing.dto.ListingReportRequest;
import com.customhouse.domain.support.dto.PolicyCorrectionRequest;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.mail.MailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * [담당: 송귀성] 고객센터 - 정책정보 정정신고 메일 구성 테스트. 실제 메일은 보내지 않고 MailClient를 목으로 바꿔
 * 수신자/제목/본문/답장 주소와 메일 헤더 주입 차단, 발송 실패 처리를 확인한다.
 */
class SupportMailServiceTest {

    private MailClient mailClient;
    private SupportMailService service;

    @BeforeEach
    void setUp() {
        mailClient = mock(MailClient.class);
        service = new SupportMailService(mailClient);
        ReflectionTestUtils.setField(service, "receiverEmail", "admin@example.com");
    }

    private PolicyCorrectionRequest request(String type, String policyName, String message) {
        return new PolicyCorrectionRequest("김테스트", "tester@example.com", type, "POLICY_123", policyName,
                "강남구청", "강남구", "월 20만원 지원", message);
    }

    @Test
    void 관리자에게_정정신고_메일을_구성해_보낸다() {
        service.sendPolicyCorrection(request("EXPIRED", "청년 월세 지원", "이 정책은 올해 종료되었습니다."));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(org.mockito.ArgumentMatchers.eq("admin@example.com"), subject.capture(),
                text.capture(), org.mockito.ArgumentMatchers.eq("tester@example.com"));

        assertThat(subject.getValue()).isEqualTo("[맞집 정책정보 정정신고] 종료되었거나 변경된 정책 - 청년 월세 지원");
        assertThat(text.getValue())
                .contains("김테스트 <tester@example.com>", "POLICY_123", "강남구청", "강남구", "월 20만원 지원")
                .contains("이 정책은 올해 종료되었습니다.");
    }

    @Test
    void 정책을_고르지_않으면_제목에_유형만_들어간다() {
        PolicyCorrectionRequest noPolicy = new PolicyCorrectionRequest("김테스트", "tester@example.com", "MISSING",
                null, null, null, null, null, "마포구에 이런 정책이 있어요.");
        service.sendPolicyCorrection(noPolicy);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(any(), subject.capture(), any(), any());
        assertThat(subject.getValue()).isEqualTo("[맞집 정책정보 정정신고] 누락된 정책 제보");
    }

    @Test
    void 정책명의_줄바꿈은_제목에서_제거되어_메일_헤더_주입을_막는다() {
        service.sendPolicyCorrection(request("OTHER", "정책\r\nBcc: attacker@example.com", "내용"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(any(), subject.capture(), any(), any());
        assertThat(subject.getValue()).doesNotContain("\r").doesNotContain("\n");
        assertThat(subject.getValue()).isEqualTo("[맞집 정책정보 정정신고] 기타 - 정책 Bcc: attacker@example.com"); // 한 줄 문구일 뿐 별도 헤더가 아니다
    }

    private ListingReportRequest listingReport(String address, String message) {
        return new ListingReportRequest("ALREADY_SOLD", message, null, address, "월세", 1000, 60);
    }

    @Test
    void 허위매물_신고_메일을_관리자에게_구성해_보낸다() {
        service.sendListingReport("회원", "member@example.com", "SEOCHO-202609-0001",
                listingReport("서울 서초구 잠원로14길 42", "이미 계약된 매물입니다."), 2);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(org.mockito.ArgumentMatchers.eq("admin@example.com"), subject.capture(),
                text.capture(), org.mockito.ArgumentMatchers.eq("member@example.com"));

        assertThat(subject.getValue()).isEqualTo("[맞집 허위매물 신고] 이미 계약된 매물 - SEOCHO-202609-0001");
        assertThat(text.getValue())
                .contains("회원 <member@example.com>", "누적 신고: 2건", "SEOCHO-202609-0001", "서울 서초구 잠원로14길 42",
                        "보증금 1000만원", "월세 60만원", "이미 계약된 매물입니다.");
    }

    @Test
    void 허위매물_신고의_주소_줄바꿈은_본문에서_한_줄로_정리된다() {
        service.sendListingReport("회원", "member@example.com", "SEOCHO-1\r\nBcc: attacker@example.com",
                listingReport("주소\r\nBcc: attacker@example.com", "내용"), 1);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(any(), subject.capture(), any(), any());
        assertThat(subject.getValue()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void 허위매물_신고_메일_발송이_실패하면_MAIL_SEND_FAILED_예외를_던진다() {
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED)).when(mailClient).send(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sendListingReport("회원", "member@example.com", "SEOCHO-1", listingReport("주소", "내용"), 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MAIL_SEND_FAILED);
    }

    @Test
    void 메일_발송이_실패하면_MAIL_SEND_FAILED_예외를_던진다() {
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED)).when(mailClient).send(any(), any(), any(), any());

        assertThatThrownBy(() -> service.sendPolicyCorrection(request("INFO_ERROR", "청년 월세 지원", "금액이 다릅니다.")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MAIL_SEND_FAILED);
    }
}
