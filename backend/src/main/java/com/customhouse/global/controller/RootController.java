package com.customhouse.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 허겸] 공통 인프라 - 루트 경로(/) 안내.
 * 브라우저로 서버 기본 주소에 접속했을 때 에러 JSON 대신 "서버 작동 중" 문구를 보여준다 (팀원 제안, 2026-09-28).
 * SecurityConfig가 anyRequest().permitAll()이라 인증 없이 접근된다.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public String healthCheck() {
        return "Customhouse Backend Server is running!";
    }
}
