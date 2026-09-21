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
        requestFactory.setReadTimeout(30_000);

        return RestClient.builder()
                .baseUrl(aiEngineBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
