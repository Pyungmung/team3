package com.customhouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 맞집(CustomHouse) 백엔드 애플리케이션 엔트리포인트.
 * 공통 인프라(전역 설정) 담당: 허겸
 */
@SpringBootApplication
public class CustomHouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomHouseApplication.class, args);
    }
}
