package com.customhouse.domain.ad.service;

// [담당: 송귀성] 광고 중인 매물번호 조회 - 1분 캐시와 evict 동작, 조회 시각 전달 확인 (2026-10-08).

import com.customhouse.domain.ad.repository.ListingAdRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveAdServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 지금_시각_기준으로_조회하고_연속_호출은_캐시를_쓰며_evict하면_다시_읽는다() {
        ListingAdRepository repo = mock(ListingAdRepository.class);
        when(repo.findActiveListingIds(LocalDateTime.of(2026, 10, 8, 12, 0))).thenReturn(List.of("A-1", "B-2"));
        ActiveAdService service = new ActiveAdService(repo, CLOCK);

        assertThat(service.activeListingIds()).containsExactly("A-1", "B-2");
        assertThat(service.activeListingIds()).containsExactly("A-1", "B-2");
        verify(repo, times(1)).findActiveListingIds(LocalDateTime.of(2026, 10, 8, 12, 0));

        service.evict();
        service.activeListingIds();
        verify(repo, times(2)).findActiveListingIds(LocalDateTime.of(2026, 10, 8, 12, 0));
    }
}
