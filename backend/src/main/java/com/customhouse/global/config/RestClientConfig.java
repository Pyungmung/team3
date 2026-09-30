package com.customhouse.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * [담당: 허겸] 공통 인프라 - 외부 서버(Python AI 엔진) 호출용 RestClient 빈 설정.
 * 실제 호출 로직은 domain/recommendation (담당: 송귀성)에서 이 빈을 사용한다.
 *
 * 요청 팩토리로 SimpleClientHttpRequestFactory(HttpURLConnection 기반)를 명시적으로 사용한다.
 * 기본값인 JDK HttpClient는 HTTP/1.1 upgrade 헤더를 붙여 uvicorn(FastAPI)과 통신 시
 * "Unsupported upgrade request" 오류를 일으킬 수 있기 때문이다.
 */
@Configuration
public class RestClientConfig {

    @Value("${ai-engine.base-url}")
    private String aiEngineBaseUrl;

    @Bean
    public RestClient aiEngineRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        // AI 엔진이 국토부 실거래가 4종을 지역별로 조회하므로 콜드 스타트 때 시간이 걸린다 (병렬 호출로 줄였지만 여유를 둔다).
        // 2026-09-28: 국토부 응답을 페이지네이션으로 전량 수집하고, 최종 추천 매물마다 도로명주소·좌표·통근시간을
        // 외부 API로 처음 조회하는 콜드 진단이 실측 약 45초(캐시가 채워지면 2~3초)라 30초로는 502가 나서 늘렸다.
        requestFactory.setReadTimeout(120_000);

        return RestClient.builder()
                .baseUrl(aiEngineBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** [담당: 허겸] SendGrid HTTP API(이메일 발송)용 RestClient. global/mail/MailClient.java 참고. */
    @Bean
    public RestClient sendgridRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.sendgrid.com")
                .build();
    }
}
