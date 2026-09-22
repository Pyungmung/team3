package com.customhouse.domain.support.controller;

import com.customhouse.domain.support.dto.InquiryRequest;
import com.customhouse.domain.support.service.SupportMailService;
import com.customhouse.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 미정] 고객센터 - 이메일 문의.
 * 프론트엔드(양혜승, mypage/edit-condition.html 고객센터 탭)에서 호출.
 * 로그인 여부와 무관하게 누구나 문의를 보낼 수 있다 (SecurityConfig의 /api/support/** permitAll).
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportMailService supportMailService;

    @PostMapping("/inquiries")
    public ResponseEntity<ApiResponse<Void>> submitInquiry(@Valid @RequestBody InquiryRequest request) {
        supportMailService.sendInquiry(request);
        return ResponseEntity.ok(ApiResponse.ok("문의가 접수되었습니다.", null));
    }
}
