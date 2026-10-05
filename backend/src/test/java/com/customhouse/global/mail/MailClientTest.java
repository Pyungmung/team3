package com.customhouse.global.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * [담당: 송귀성] 메일 수신 주소 바꿔치기 테스트 - 관리자 로그인 이메일(admin@admin.com)로 가는 메일은 실제 수신 주소로 보내고,
 * 설정이 비어 있거나 다른 주소면 그대로 보낸다. 실제 SendGrid 호출은 목으로 바꾼다.
 */
class MailClientTest {

    private RestClient restClient;
    private RestClient.RequestBodySpec bodySpec;
    private MailClient client;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(restClient.post()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri("/v3/mail/send")).thenReturn(bodySpec);
        org.mockito.Mockito.when(bodySpec.header(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(bodySpec);
        org.mockito.Mockito.when(bodySpec.contentType(org.mockito.ArgumentMatchers.any())).thenReturn(bodySpec);
        org.mockito.Mockito.when(bodySpec.body(org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(bodySpec);

        client = new MailClient(restClient);
        ReflectionTestUtils.setField(client, "apiKey", "KEY");
        ReflectionTestUtils.setField(client, "fromEmail", "from@example.com");
        ReflectionTestUtils.setField(client, "adminLoginEmail", "admin@admin.com");
    }

    @Test
    void 관리자_로그인_이메일로_가는_메일은_실제_수신_주소로_바꿔_보낸다() {
        ReflectionTestUtils.setField(client, "adminMailEmail", "real@example.com");

        assertThat(client.resolveRecipient("admin@admin.com")).isEqualTo("real@example.com");
        assertThat(client.resolveRecipient(" Admin@Admin.com ")).isEqualTo("real@example.com");   // 대소문자/공백 무시
    }

    @Test
    void 다른_주소는_그대로_보낸다() {
        ReflectionTestUtils.setField(client, "adminMailEmail", "real@example.com");

        assertThat(client.resolveRecipient("member@example.com")).isEqualTo("member@example.com");
        assertThat(client.resolveRecipient("real@example.com")).isEqualTo("real@example.com");
        assertThat(client.resolveRecipient(null)).isNull();
    }

    @Test
    void 수신_주소를_설정하지_않으면_관리자_이메일도_바꾸지_않는다() {
        ReflectionTestUtils.setField(client, "adminMailEmail", "");

        assertThat(client.resolveRecipient("admin@admin.com")).isEqualTo("admin@admin.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 실제_발송_요청의_받는_사람도_바뀐_주소다() {
        ReflectionTestUtils.setField(client, "adminMailEmail", "real@example.com");

        client.send("admin@admin.com", "제목", "본문", null);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(body.capture());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        List<Map<String, Object>> personalizations = (List<Map<String, Object>>) sent.get("personalizations");
        List<Map<String, String>> to = (List<Map<String, String>>) personalizations.get(0).get("to");
        assertThat(to.get(0).get("email")).isEqualTo("real@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 일반_회원에게_보내는_발송_요청은_받는_사람이_그대로다() {
        ReflectionTestUtils.setField(client, "adminMailEmail", "real@example.com");

        client.send("member@example.com", "제목", "본문", null);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec).body(body.capture());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        List<Map<String, Object>> personalizations = (List<Map<String, Object>>) sent.get("personalizations");
        List<Map<String, String>> to = (List<Map<String, String>>) personalizations.get(0).get("to");
        assertThat(to.get(0).get("email")).isEqualTo("member@example.com");
    }
}
