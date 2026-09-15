package com.customhouse.domain.payment.service;

import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * [담당: 황진구] 결제 도메인 - 토스페이먼츠 Open API 클라이언트
 * 인증: Basic base64(시크릿키 + ":") (https://docs.tosspayments.com/reference/using-api/authorization)
 *
 * toss.secret-key 기본값은 토스페이먼츠 공식 샘플 저장소(tosspayments/tosspayments-sample)에 공개된
 * "API 개별 연동 키"용 테스트 시크릿 키입니다 (test_sk_...) - 실제 결제는 발생하지 않는 샌드박스 키이며
 * 누구나 바로 테스트해볼 수 있도록 토스페이먼츠가 문서에 공개한 값입니다. 실서비스 전환 시
 * 개발자센터에서 발급받은 라이브 키(live_sk_...)로 backend/.env에서 교체해야 합니다.
 */
@Component
public class TossPaymentsClient {

    private final RestClient restClient;

    public TossPaymentsClient(@Value("${toss.secret-key}") String secretKey) {
        String credentials = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 단건 결제 승인. @see https://docs.tosspayments.com/reference#결제-승인 */
    public Map<String, Object> confirmPayment(String paymentKey, String orderId, Long amount) {
        return post("/v1/payments/confirm", Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        ));
    }

    /** authKey로 빌링키(자동결제 수단) 발급. @see https://docs.tosspayments.com/reference#자동결제-빌링키-발급 */
    public Map<String, Object> issueBillingKey(String customerKey, String authKey) {
        return post("/v1/billing/authorizations/issue", Map.of(
                "customerKey", customerKey,
                "authKey", authKey
        ));
    }

    /** 발급된 빌링키로 자동결제 실행. @see https://docs.tosspayments.com/reference#자동결제 */
    public Map<String, Object> chargeBilling(String billingKey, String customerKey, Long amount, String orderId, String orderName) {
        return post("/v1/billing/" + billingKey, Map.of(
                "customerKey", customerKey,
                "amount", amount,
                "orderId", orderId,
                "orderName", orderName
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String uri, Map<String, Object> body) {
        try {
            return restClient.post().uri(uri).body(body).retrieve().body(Map.class);
        } catch (RestClientResponseException e) {
            throw new CustomException(ErrorCode.PAYMENT_FAILED, extractMessage(e));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(RestClientResponseException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("message") != null) {
                return (String) body.get("message");
            }
        } catch (Exception ignored) {
            // 응답 파싱 실패 시 아래 기본 메시지 사용
        }
        return "토스페이먼츠 요청이 실패했습니다 (HTTP " + e.getStatusCode() + ")";
    }
}
