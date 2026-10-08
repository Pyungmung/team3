package com.customhouse.domain.banner;

// [담당: 송귀성] 직접 배너 광고 전체 흐름 통합 테스트 (SpringBootTest + 실제 JWT/보안 + H2): 공개 목록은 로그인 없이 200,
// 관리자만 등록/수정/삭제(일반 사용자 403, 비로그인 401), 위험 링크는 400, 등록한 배너가 공개 목록에 바로 반영되는지 확인한다 (2026-10-08).
// 운영/로컬 공유 DB가 아니라 테스트용 H2에서만 돈다.

import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import com.customhouse.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BannerFlowIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwt;

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

    private static String bannerJson(String title, String link, String extra) {
        return "{\"title\":\"" + title + "\",\"category\":\"MOVING\",\"imageUrl\":\"/uploads/listings/test.png\",\"linkUrl\":\"" + link + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    @Test
    void 공개_목록은_로그인_없이_조회되고_관리자가_등록하면_바로_반영된다() throws Exception {
        User admin = saveUser("admin", User.ROLE_ADMIN);
        String title = "통합테스트-" + UUID.randomUUID();

        JsonNode created = body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON)
                .content(bannerJson(title, "https://example.com/move", "\"segment\":\"PREMIUM\",\"regions\":[\"서초구\"],\"moveWithin\":\"WITHIN_3M\",\"slots\":[\"home-bottom\"]")),
                token(admin), 200).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("segment").asText()).isEqualTo("PREMIUM");
        assertThat(created.get("regions").get(0).asText()).isEqualTo("서초구");

        JsonNode publicList = body(get("/api/banners"), null, 200).get("data");
        JsonNode row = null;
        for (JsonNode n : publicList) if (n.get("id").asLong() == id) row = n;
        assertThat(row).isNotNull();
        assertThat(row.get("title").asText()).isEqualTo(title);

        // 수정해서 끄면 공개 목록에서 빠지고, 관리자 목록에는 남는다
        body(put("/api/admin/banners/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(bannerJson(title, "https://example.com/move", "\"active\":false")), token(admin), 200);
        for (JsonNode n : body(get("/api/banners"), null, 200).get("data")) assertThat(n.get("id").asLong()).isNotEqualTo(id);
        boolean inAdminList = false;
        for (JsonNode n : body(get("/api/admin/banners"), token(admin), 200).get("data")) inAdminList |= n.get("id").asLong() == id;
        assertThat(inAdminList).isTrue();

        body(delete("/api/admin/banners/" + id), token(admin), 200);
        body(delete("/api/admin/banners/" + id), token(admin), 404);
    }

    @Test
    void 관리자가_아니면_배너를_관리할_수_없다() throws Exception {
        User user = saveUser("plain", User.ROLE_USER);
        String content = bannerJson("x", "https://example.com", null);

        body(get("/api/admin/banners"), token(user), 403);
        body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON).content(content), token(user), 403);
        body(put("/api/admin/banners/1").contentType(MediaType.APPLICATION_JSON).content(content), token(user), 403);
        body(delete("/api/admin/banners/1"), token(user), 403);
        body(get("/api/admin/banners"), null, 401);
        body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON).content(content), null, 401);
    }

    @Test
    void 위험한_링크나_빠진_값은_400이다() throws Exception {
        User admin = saveUser("admin", User.ROLE_ADMIN);

        body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON).content(bannerJson("x", "javascript:alert(1)", null)), token(admin), 400);
        body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON)
                .content(bannerJson("x", "https://example.com", "\"regions\":[\"부산진구\"]")), token(admin), 400);
        body(post("/api/admin/banners").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"category\":\"MOVING\"}"), token(admin), 400);
    }
}
