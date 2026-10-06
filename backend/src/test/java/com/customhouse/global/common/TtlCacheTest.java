package com.customhouse.global.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [담당: 송귀성] TtlCache 테스트 - 시간 안에는 다시 읽지 않고, 비우거나 시간이 지나면 새로 읽는다.
 */
class TtlCacheTest {

    @Test
    void 시간_안에는_로더를_다시_부르지_않는다() {
        TtlCache<String> cache = new TtlCache<>(60_000);
        AtomicInteger calls = new AtomicInteger();

        assertThat(cache.get(() -> "v" + calls.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.get(() -> "v" + calls.incrementAndGet())).isEqualTo("v1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 비우면_다음에_새로_읽는다() {
        TtlCache<String> cache = new TtlCache<>(60_000);
        AtomicInteger calls = new AtomicInteger();
        cache.get(() -> "v" + calls.incrementAndGet());

        cache.evictAfterCommit();   // 트랜잭션이 없으면 바로 비운다

        assertThat(cache.get(() -> "v" + calls.incrementAndGet())).isEqualTo("v2");
    }

    @Test
    void 시간이_지나면_새로_읽는다() throws Exception {
        TtlCache<String> cache = new TtlCache<>(20);
        AtomicInteger calls = new AtomicInteger();
        cache.get(() -> "v" + calls.incrementAndGet());

        Thread.sleep(40);

        assertThat(cache.get(() -> "v" + calls.incrementAndGet())).isEqualTo("v2");
    }
}
