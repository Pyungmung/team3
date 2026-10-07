package com.customhouse.global.config;

// [담당: 송귀성] AI 엔진이 입력 오류(400/422)로 답하면 502가 아니라 400(VALIDATION_ERROR)+읽기 쉬운 문구로 바뀌는지,
// 404/5xx는 예전 동작(HttpClientErrorException.NotFound / RestClientException 계열)이 그대로인지 가짜 AI 엔진 서버로 확인한다 (2026-10-07).

import com.customhouse.domain.recommendation.service.AiEngineErrorMessage;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEngineRestClientErrorTest {

    private HttpServer server;
    private RestClient client;

    @BeforeEach
    void startFakeAiEngine() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        reply("/bad-location", 400, "{\"detail\":\"지원하지 않는 직장 위치입니다: '없는구'. 현재 지원 지역: 강남구, 강동구\"}");
        reply("/bad-age", 422, "{\"detail\":[{\"type\":\"less_than_equal\",\"loc\":[\"body\",\"age\"],\"msg\":\"must be <= 120\"},"
                + "{\"type\":\"missing\",\"loc\":[\"body\",\"annualIncome\"],\"msg\":\"Field required\"}]}");
        reply("/not-found", 404, "{\"detail\":\"매물을 찾을 수 없어요.\"}");
        reply("/boom", 500, "{\"detail\":\"internal\"}");
        reply("/ok", 200, "{\"ok\":true}");
        server.start();

        RestClientConfig config = new RestClientConfig();
        ReflectionTestUtils.setField(config, "aiEngineBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        client = config.aiEngineRestClient();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void reply(String path, int status, String json) {
        server.createContext(path, exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void 입력_오류_400은_VALIDATION_ERROR와_엔진이_준_문구로_바뀐다() {
        assertThatThrownBy(() -> client.get().uri("/bad-location").retrieve().body(Map.class))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.getMessage()).startsWith("지원하지 않는 직장 위치입니다").doesNotContain("AI 추천 엔진").doesNotContain("400 Bad Request");
                });
    }

    @Test
    void 필드_검증_422는_화면에서_쓰는_이름으로_알려준다() {
        assertThatThrownBy(() -> client.post().uri("/bad-age").retrieve().body(Map.class))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.getMessage()).isEqualTo("입력값을 확인해주세요: 나이, 연소득");
                });
    }

    @Test
    void 매물없음_404는_그대로_NotFound로_남아_삭제된_매물_안내가_동작한다() {
        assertThatThrownBy(() -> client.get().uri("/not-found").retrieve().body(Map.class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void 엔진_내부_오류_5xx는_변환하지_않는다() {
        assertThatThrownBy(() -> client.get().uri("/boom").retrieve().body(Map.class))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void 정상_응답은_그대로_읽는다() {
        assertThat(client.get().uri("/ok").retrieve().body(Map.class)).containsEntry("ok", true);
    }

    @Test
    void 읽을_수_없는_본문은_일반_문구로() {
        assertThat(AiEngineErrorMessage.from(null)).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(AiEngineErrorMessage.from("not json")).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(AiEngineErrorMessage.from("{\"detail\":[]}")).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(AiEngineErrorMessage.from("{\"detail\":[{\"loc\":[\"body\",\"mystery\"]}]}")).isEqualTo("입력값을 확인해주세요: mystery");
    }
}
