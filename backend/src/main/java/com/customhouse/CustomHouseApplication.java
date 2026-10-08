package com.customhouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * 맞집(CustomHouse) 백엔드 애플리케이션 엔트리포인트.
 * 공통 인프라(전역 설정) 담당: 허겸
 *
 * 2026-10-08: 서버 기본 시간대를 한국(Asia/Seoul)으로 고정한다. Render 서버는 UTC라서 알림/글 시각이 9시간 전으로 보였다
 * (DB 연결 주소의 serverTimezone=Asia/Seoul과 JVM 시간대가 다르면 읽을 때 시각이 어긋난다). 로컬(한국 시간)과 배포가 같은 DB를 쓰므로 둘을 맞춘다.
 */
@SpringBootApplication
public class CustomHouseApplication {

    /** 서버 기본 시간대(한국). 스프링/하이버네이트/JDBC가 뜨기 전에 먼저 적용해야 해서 main에서 가장 먼저 부른다. */
    public static final String SERVER_TIME_ZONE = "Asia/Seoul";

    public static void applyServerTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SERVER_TIME_ZONE));
    }

    public static void main(String[] args) {
        applyServerTimeZone();
        SpringApplication.run(CustomHouseApplication.class, args);
    }
}
