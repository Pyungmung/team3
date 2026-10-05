package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingFavoriteResponse;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.entity.ListingFavorite;
import com.customhouse.domain.listing.repository.ListingFavoriteRepository;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 관심매물 새로고침 테스트 - 매물번호로 AI 엔진에서 새로 계산한 카드를 받아 가격과 카드 전체 정보(snapshot)가
 * 갱신되는지, 담지 않은 매물/삭제된 매물은 거절되는지 확인한다.
 */
class ListingFavoriteServiceTest {

    private static final String LISTING = "SEOCHO-202610-0001";

    private ListingFavoriteRepository favoriteRepository;
    private ListingReportService reportService;
    private AiEngineClient aiEngineClient;
    private RecommendRequest condition;
    private ListingFavoriteService service;

    @BeforeEach
    void setUp() {
        favoriteRepository = mock(ListingFavoriteRepository.class);
        reportService = mock(ListingReportService.class);
        aiEngineClient = mock(AiEngineClient.class);
        condition = mock(RecommendRequest.class);
        when(condition.transportType()).thenReturn("PUBLIC");
        when(reportService.getStatuses(any())).thenReturn(Map.of(LISTING, new ListingReportStatus(0, false)));
        service = new ListingFavoriteService(favoriteRepository, reportService, aiEngineClient);
    }

    private ListingFavorite priceChangedFavorite() {
        ListingFavorite fav = ListingFavorite.of(1L, LISTING, "서울 서초구 잠원로14길 42", "서초구", "월세",
                "잠원 하이츠", "연립다세대", "301호", 5000, 80, 5, "{\"listing_deposit\":5000}");
        fav.applyPriceChange(6000, 90);   // 간단 카드(snapshot 없음) 상태
        return fav;
    }

    @Test
    void 새로고침하면_새_가격과_카드_전체_정보로_갱신된다() {
        ListingFavorite fav = priceChangedFavorite();
        when(favoriteRepository.findByUserIdAndListingId(1L, LISTING)).thenReturn(Optional.of(fav));
        when(aiEngineClient.requestListingRefresh(LISTING, condition)).thenReturn(Map.of(
                "listing", Map.of("listing_id", LISTING, "listing_deposit", 6000, "listing_monthly_rent", 90, "maintenance_fee", 7),
                "deposit_conversion_rate", 5.1));

        ListingFavoriteResponse response = service.refresh(1L, LISTING, condition);

        assertThat(fav.getDeposit()).isEqualTo(6000);
        assertThat(fav.getMonthlyRent()).isEqualTo(90);
        assertThat(fav.getMaintenanceFee()).isEqualTo(7);
        assertThat(fav.getSnapshot()).contains("\"listing_deposit\":6000")
                .contains("\"_conversion_rate\":5.1").contains("\"_transport_type\":\"PUBLIC\"");
        assertThat(fav.getPriceChangedAt()).isNotNull();   // 가격 변동 안내 이력은 그대로 둔다
        assertThat(response.snapshot()).isEqualTo(fav.getSnapshot());
        verify(favoriteRepository).save(fav);
    }

    @Test
    void 담지_않은_매물은_새로고침할_수_없다() {
        when(favoriteRepository.findByUserIdAndListingId(1L, LISTING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(1L, LISTING, condition)).isInstanceOf(CustomException.class);
        verify(aiEngineClient, never()).requestListingRefresh(any(), any());
    }

    @Test
    void 매물_정보를_받지_못하면_관심매물을_바꾸지_않는다() {
        ListingFavorite fav = priceChangedFavorite();
        when(favoriteRepository.findByUserIdAndListingId(1L, LISTING)).thenReturn(Optional.of(fav));
        when(aiEngineClient.requestListingRefresh(LISTING, condition)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.refresh(1L, LISTING, condition)).isInstanceOf(CustomException.class);
        assertThat(fav.getSnapshot()).isNull();
        verify(favoriteRepository, never()).save(any());
    }
}
