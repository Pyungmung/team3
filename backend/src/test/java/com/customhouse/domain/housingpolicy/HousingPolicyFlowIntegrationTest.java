package com.customhouse.domain.housingpolicy;

// [담당: 송귀성] 주거지원정책 전체 흐름 통합 테스트 (SpringBootTest + 실제 JWT/보안 + H2): 관리자가 정책을 추가/수정/삭제하면
// 관심정책을 담은 회원의 관심정책 조회에 반영되고 알림함에 알림이 쌓이며, 관리자가 아니면 막히는지 확인한다 (2026-10-08).
// 운영/로컬 공유 DB가 아니라 테스트용 H2에서만 돈다.

import com.customhouse.domain.housingpolicy.service.HousingPolicyService;
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
class HousingPolicyFlowIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwt;
    @Autowired
    private HousingPolicyService policyService;

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

    private static String policyJson(String name, String description, String link, String note) {
        return "{\"region\":\"강남구\",\"agency\":\"서울시\",\"name\":\"" + name + "\",\"description\":\"" + description + "\","
                + "\"minAge\":19,\"maxAge\":39,\"maxAnnualIncome\":5000,\"maxAsset\":null,\"medianIncomePercent\":null,"
                + "\"requireBasicLivelihood\":false,\"requireSme\":false,\"requireNewlywed\":false,\"requireNoHousehold\":true,"
                + "\"loan\":false,\"note\":" + (note == null ? "null" : "\"" + note + "\"") + ",\"link\":" + (link == null ? "null" : "\"" + link + "\"") + "}";
    }

    @Test
    void 관리자가_정책을_고치고_지우면_관심정책_담은_회원에게_반영되고_알림이_쌓인다() throws Exception {
        User admin = saveUser("admin", User.ROLE_ADMIN);
        User userA = saveUser("a", User.ROLE_USER);
        User userB = saveUser("b", User.ROLE_USER);
        String name = "통합테스트-" + UUID.randomUUID();

        // 1) 관리자가 정책 추가 -> 진단 요청용 목록에도 바로 나온다(캐시가 비워진다)
        JsonNode created = body(post("/api/admin/housing-policies").contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(name, "월 10만원", "https://old.go.kr", "메모")), token(admin), 200);
        long policyId = created.get("data").get("policy").get("id").asLong();
        assertThat(created.get("data").get("notifiedUsers").asInt()).isZero();
        assertThat(policyService.getForEngine()).anyMatch(p -> p.id() == policyId && p.name().equals(name) && p.requireNoHousehold());

        // 2) 두 회원이 관심정책으로 담는다 (두 번 담아도 한 건)
        for (User u : new User[]{userA, userB}) {
            body(post("/api/watchlist/policies").contentType(MediaType.APPLICATION_JSON).content("{\"policyId\":" + policyId + "}"), token(u), 200);
        }
        body(post("/api/watchlist/policies").contentType(MediaType.APPLICATION_JSON).content("{\"policyId\":" + policyId + "}"), token(userA), 200);
        JsonNode listA = body(get("/api/watchlist/policies"), token(userA), 200).get("data");
        assertThat(listA).hasSize(1);
        assertThat(listA.get(0).get("name").asText()).isEqualTo(name);
        assertThat(body(get("/api/watchlist/policies/ids"), token(userA), 200).get("data").get(0).asLong()).isEqualTo(policyId);

        // 3) 지원혜택/링크 수정 -> 두 회원에게 알림, 관심정책 조회에는 새 내용이 바로 보인다
        JsonNode edited = body(put("/api/admin/housing-policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(name, "월 20만원", "https://new.go.kr", "메모")), token(admin), 200);
        assertThat(edited.get("data").get("notifiedUsers").asInt()).isEqualTo(2);
        JsonNode afterEdit = body(get("/api/watchlist/policies"), token(userA), 200).get("data").get(0);
        assertThat(afterEdit.get("description").asText()).isEqualTo("월 20만원");
        assertThat(afterEdit.get("link").asText()).isEqualTo("https://new.go.kr");
        JsonNode notiA = body(get("/api/notifications"), token(userA), 200).get("data");
        assertThat(notiA).hasSize(1);
        assertThat(notiA.get(0).get("title").asText()).isEqualTo("관심 정책 변경");
        assertThat(notiA.get(0).get("content").asText()).contains(name).contains("지원혜택").contains("링크");
        assertThat(body(get("/api/notifications/unread-count"), token(userA), 200).get("data").get("count").asInt()).isEqualTo(1);

        // 4) 관리자용 메모만 바꾸면 알림이 가지 않는다
        JsonNode memoOnly = body(put("/api/admin/housing-policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(name, "월 20만원", "https://new.go.kr", "새 메모")), token(admin), 200);
        assertThat(memoOnly.get("data").get("notifiedUsers").asInt()).isZero();

        // 5) A가 관심정책에서 빼면 이후 변경 알림은 B에게만 간다
        body(delete("/api/watchlist/policies/" + policyId), token(userA), 200);
        assertThat(body(get("/api/watchlist/policies"), token(userA), 200).get("data")).isEmpty();
        JsonNode second = body(put("/api/admin/housing-policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
                .content(policyJson(name, "월 30만원", "https://new.go.kr", "새 메모")), token(admin), 200);
        assertThat(second.get("data").get("notifiedUsers").asInt()).isEqualTo(1);

        // 6) 정책을 삭제하면 B의 관심정책에서 빠지고 삭제 알림이 간다
        JsonNode deleted = body(delete("/api/admin/housing-policies/" + policyId), token(admin), 200);
        assertThat(deleted.get("data").get("notifiedUsers").asInt()).isEqualTo(1);
        assertThat(body(get("/api/watchlist/policies"), token(userB), 200).get("data")).isEmpty();
        JsonNode notiB = body(get("/api/notifications"), token(userB), 200).get("data");
        assertThat(notiB).extracting(n -> n.get("title").asText()).contains("관심 정책 삭제", "관심 정책 변경");
        assertThat(policyService.getForEngine()).noneMatch(p -> p.id() == policyId);

        // 7) 삭제된 정책은 더 담을 수 없다 (찾을 수 없음)
        body(post("/api/watchlist/policies").contentType(MediaType.APPLICATION_JSON).content("{\"policyId\":" + policyId + "}"), token(userA), 404);
    }

    @Test
    void 관리자가_아니면_정책_관리를_못하고_로그인_없이는_관심정책을_못_쓴다() throws Exception {
        User user = saveUser("plain", User.ROLE_USER);

        body(get("/api/admin/housing-policies"), token(user), 403);
        body(post("/api/admin/housing-policies").contentType(MediaType.APPLICATION_JSON).content(policyJson("x", "y", null, null)), token(user), 403);
        body(delete("/api/admin/housing-policies/1"), token(user), 403);
        body(get("/api/admin/housing-policies"), null, 401);
        body(get("/api/watchlist/policies"), null, 401);
        body(post("/api/watchlist/policies").contentType(MediaType.APPLICATION_JSON).content("{\"policyId\":1}"), null, 401);
    }

    @Test
    void 잘못된_입력은_400으로_거절한다() throws Exception {
        User admin = saveUser("admin2", User.ROLE_ADMIN);

        body(post("/api/admin/housing-policies").contentType(MediaType.APPLICATION_JSON)
                .content(policyJson("정책", "내용", "javascript:alert(1)", null)), token(admin), 400);
        body(post("/api/admin/housing-policies").contentType(MediaType.APPLICATION_JSON)
                .content(policyJson("정책", "내용", null, null).replace("\"강남구\"", "\"부산\"")), token(admin), 400);
        body(post("/api/admin/housing-policies").contentType(MediaType.APPLICATION_JSON)
                .content(policyJson("정책", "내용", null, null).replace("\"minAge\":19", "\"minAge\":50")), token(admin), 400);
        body(put("/api/admin/housing-policies/99999999").contentType(MediaType.APPLICATION_JSON).content(policyJson("정책", "내용", null, null)), token(admin), 404);
    }
}
