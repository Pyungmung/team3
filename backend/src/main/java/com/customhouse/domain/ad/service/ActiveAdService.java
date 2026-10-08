package com.customhouse.domain.ad.service;

import com.customhouse.domain.ad.repository.ListingAdRepository;
import com.customhouse.global.common.TtlCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [담당: 송귀성] "지금 광고 중인 매물번호" 조회 (2026-10-08). 진단 요청마다 AI 엔진에 실어 보내는 값이라 1분 캐시하고,
 * 광고가 접수/환불되면 AdService가 바로 비운다. AdService(매물 등록까지 하므로 AiEngineClient에 의존)와 분리해 둔 이유는
 * AiEngineClient가 이 값을 읽어야 해서, 같은 클래스에 두면 서로를 물고 도는 순환 의존이 생기기 때문이다.
 */
@Service
public class ActiveAdService {

    private final ListingAdRepository adRepository;
    private final Clock clock;
    private final TtlCache<List<String>> cache = new TtlCache<>(60 * 1000L);

    @Autowired
    public ActiveAdService(ListingAdRepository adRepository) {
        this(adRepository, Clock.systemDefaultZone());
    }

    ActiveAdService(ListingAdRepository adRepository, Clock clock) {
        this.adRepository = adRepository;
        this.clock = clock;
    }

    public List<String> activeListingIds() {
        return cache.get(() -> List.copyOf(adRepository.findActiveListingIds(LocalDateTime.now(clock))));
    }

    /** 광고가 새로 접수/연장/환불됐을 때 호출해 다음 진단부터 바로 반영되게 한다. */
    public void evict() {
        cache.evict();
    }
}
