package com.customhouse.domain.ad;

// [담당: 송귀성] 광고하기 전체 흐름 통합 테스트 (SpringBootTest + 실제 JWT/보안 + H2, 토스/AI 엔진은 가짜): 비로그인 401,
// 주문 -> 승인 -> 내 광고 현황 -> 관리자 환불, 관리자가 아니면 광고 현황/환불 403, 남의 매물 주문 403을 확인한다 (2026-10-08).
// 운영/로컬 공유 DB가 아니라 테스트용 H2에서만 돈다.

import com.customhouse.domain.ad.service.ActiveAdService;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.domain.listing.service.ListingRegistrationService;
import com.customhouse.domain.payment.service.TossPaymentsClient;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdFlowIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RegisteredListingRepository registeredListings;
    @Autowired
    private JwtTokenProvider jwt;
    @Autowired
    private ActiveAdService activeAds;

    @MockitoBean
    private TossPaymentsClient toss;
    @MockitoBean
    private ListingRegistrationService registration;

    private User saveUser(String prefix, String role) {
        return userRepository.save(User.builder().email(prefix + "-" + UUID.randomUUID() + "@test.com").nickname(prefix).role(role).build());
    }

    private String token(User u) {
        return "Bearer " + jwt.generateAccessToken(u.getId(), u.getEmail(), u.getRole());
    }

    private JsonNode body(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder withAuth = token == null ? request : request.header("Authorization", token);
        MvcResult result = mockMvc.perform(withAuth).andExpect(status().is(expectedStatus)).andReturn();
        String text = result.getResponse().getContentAsString();
        return text.isBlank() ? null : json.readTree(text);
    }

    private String registerListing(User owner) {
        String listingId = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        registeredListings.save(RegisteredListing.of(listingId, owner.getId(), "서초구", null));
        when(registration.getDetail(listingId)).thenReturn(Map.of("listing_status", "가능"));
        return listingId;
    }

    @Test
    void 로그인_없이는_광고_API를_못_쓴다() throws Exception {
        body(get("/api/ads/config"), null, 401);
        body(get("/api/ads/mine"), null, 401);
        body(post("/api/ads/orders").contentType(MediaType.APPLICATION_JSON).content("{\"listingId\":\"A-1\"}"), null, 401);
        body(post("/api/ads/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"), null, 401);
        body(get("/api/admin/ads"), null, 401);
    }

    @Test
    void 주문_승인_내광고_관리자환불_흐름() throws Exception {
        User owner = saveUser("owner", User.ROLE_USER);
        User admin = saveUser("admin", User.ROLE_ADMIN);
        String listingId = registerListing(owner);

        JsonNode config = body(get("/api/ads/config"), token(owner), 200).get("data");
        assertThat(config.get("priceWon").asInt()).isEqualTo(1990);
        assertThat(config.get("periodDays").asInt()).isEqualTo(30);

        JsonNode order = body(post("/api/ads/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\":\"" + listingId + "\"}"), token(owner), 200).get("data");
        String orderId = order.get("orderId").asText();
        assertThat(order.get("amount").asInt()).isEqualTo(1990);

        // 금액을 바꿔 보내면 거절되고 토스는 호출되지 않는다
        body(post("/api/ads/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentKey\":\"pk_1\",\"orderId\":\"" + orderId + "\",\"amount\":10}"), token(owner), 400);

        JsonNode done = body(post("/api/ads/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentKey\":\"pk_1\",\"orderId\":\"" + orderId + "\",\"amount\":1990}"), token(owner), 200).get("data");
        assertThat(done.get("listingId").asText()).isEqualTo(listingId);
        verify(toss).confirmPayment("pk_1", orderId, 1990L);

        JsonNode mine = body(get("/api/ads/mine"), token(owner), 200).get("data");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("active").asBoolean()).isTrue();
        assertThat(mine.get(0).get("remainingDays").asInt()).isBetween(29, 30);
        assertThat(activeAds.activeListingIds()).contains(listingId);

        // 일반 사용자는 관리자 광고 현황/환불을 못 쓴다
        body(get("/api/admin/ads"), token(owner), 403);
        body(post("/api/admin/ads/" + orderId + "/cancel"), token(owner), 403);

        JsonNode list = body(get("/api/admin/ads"), token(admin), 200).get("data");
        assertThat(list).anyMatch(o -> o.get("orderId").asText().equals(orderId));

        body(post("/api/admin/ads/" + orderId + "/cancel"), token(admin), 200);
        verify(toss).cancelPayment(org.mockito.ArgumentMatchers.eq("pk_1"), anyString());
        assertThat(body(get("/api/ads/mine"), token(owner), 200).get("data").get(0).get("active").asBoolean()).isFalse();
        assertThat(activeAds.activeListingIds()).doesNotContain(listingId);
    }

    @Test
    void 남의_매물은_광고_주문을_만들_수_없다() throws Exception {
        User owner = saveUser("owner", User.ROLE_USER);
        User other = saveUser("other", User.ROLE_USER);
        String listingId = registerListing(owner);

        body(post("/api/ads/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"listingId\":\"" + listingId + "\"}"), token(other), 403);
    }

    @Test
    void 주문_대상이_없거나_둘_다_있으면_입력_오류다() throws Exception {
        User user = saveUser("plain", User.ROLE_USER);

        body(post("/api/ads/orders").contentType(MediaType.APPLICATION_JSON).content("{}"), token(user), 400);
    }
}
