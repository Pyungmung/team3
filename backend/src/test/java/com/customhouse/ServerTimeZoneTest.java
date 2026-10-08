package com.customhouse;

// [담당: 송귀성] 서버 기본 시간대를 한국(Asia/Seoul)으로 고정하는지 - UTC로 도는 배포 서버에서 시각이 9시간 전으로 보이던 문제 (2026-10-08).

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTimeZoneTest {

    private TimeZone original;

    @BeforeEach
    void remember() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void UTC로_도는_서버도_한국_시간대로_바뀌고_지금_시각이_한국_시각이다() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        CustomHouseApplication.applyServerTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
        LocalDateTime seoulNow = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        assertThat(LocalDateTime.now()).isBetween(seoulNow.minusSeconds(5), seoulNow.plusSeconds(5));
    }
}
