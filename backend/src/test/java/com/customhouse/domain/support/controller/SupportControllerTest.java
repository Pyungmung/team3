package com.customhouse.domain.support.controller;

import com.customhouse.domain.support.dto.InquiryRequest;
import com.customhouse.domain.support.service.SupportMailService;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import com.customhouse.global.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [담당: 미정] 고객센터 - 이메일 문의 컨트롤러 테스트.
 * SecurityConfig 필터 체인은 이 슬라이스 테스트 대상이 아니므로 addFilters=false로 끈다.
 * JwtAuthenticationFilter는 Filter 타입이라 @WebMvcTest 기본 스캔 대상에 포함되는데,
 * 실제 빈을 만들면 JwtTokenProvider 등 의존성이 없어 컨텍스트 로딩이 실패하므로 목으로 대체한다.
 */
@WebMvcTest(SupportController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // JacksonAutoConfiguration이 이 슬라이스에 자동으로 뜨지 않아 ObjectMapper 빈을 못 받아오므로 직접 생성한다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SupportMailService supportMailService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void 유효한_문의는_200과_함께_메일_발송을_요청한다() throws Exception {
        InquiryRequest request = new InquiryRequest("김테스트", "tester@example.com", "로그인이 안 돼요.");
        doNothing().when(supportMailService).sendInquiry(any());

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(supportMailService).sendInquiry(request);
    }

    @Test
    void 필수값이_비어있으면_400을_반환한다() throws Exception {
        InquiryRequest request = new InquiryRequest("", "not-an-email", "");

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.message").exists());
    }

    @Test
    void 메일_발송_실패시_502를_반환한다() throws Exception {
        InquiryRequest request = new InquiryRequest("김테스트", "tester@example.com", "문의합니다.");
        doThrow(new CustomException(ErrorCode.MAIL_SEND_FAILED)).when(supportMailService).sendInquiry(any());

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MAIL_SEND_FAILED"));
    }
}
