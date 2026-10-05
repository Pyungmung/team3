package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.dto.ListingRegistrationResponse;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.error.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 회원 매물 등록/삭제 서비스 테스트. AI 엔진/저장소/AdminGuard는 전부 목으로 바꿔
 * 등록 시 소유권이 기록되는지, 삭제는 본인 또는 관리자만 가능한지 확인한다.
 */
class ListingRegistrationServiceTest {

    private AiEngineClient aiEngineClient;
    private RegisteredListingRepository registeredListingRepository;
    private AdminGuard adminGuard;
    private ListingAlertService alertService;
    private ListingRegistrationService service;

    @BeforeEach
    void setUp() {
        aiEngineClient = mock(AiEngineClient.class);
        registeredListingRepository = mock(RegisteredListingRepository.class);
        adminGuard = mock(AdminGuard.class);
        alertService = mock(ListingAlertService.class);
        service = new ListingRegistrationService(aiEngineClient, registeredListingRepository, adminGuard, alertService);
    }

    private ListingRegistrationRequest request() {
        return new ListingRegistrationRequest(
                "서울특별시 강남구 테헤란로 427", "오피스텔", "월세", 5000, 80, 25.5,
                "", "", "", null, null, null, 0, "", "", null, "", "", true, "photo.jpg"
        );
    }

    @Test
    void 등록하면_AI_엔진에_그대로_전달하고_소유권을_기록한다() {
        ListingRegistrationRequest request = request();
        when(aiEngineClient.registerListing(request)).thenReturn(new ListingRegistrationResponse("GANGNAM-202610-1234", "강남구"));

        ListingRegistrationResponse result = service.register(1L, request);

        assertThat(result.listingId()).isEqualTo("GANGNAM-202610-1234");
        verify(registeredListingRepository).save(argThatOwnerIs("GANGNAM-202610-1234", 1L, "강남구"));
    }

    private RegisteredListing argThatOwnerIs(String listingId, Long userId, String region) {
        return org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getListingId().equals(listingId) && saved.getUserId().equals(userId) && saved.getRegion().equals(region));
    }

    @Test
    void 등록한_본인이면_관리자_확인_없이_삭제된다() {
        RegisteredListing owner = RegisteredListing.of("GANGNAM-202610-1234", 1L, "강남구");
        when(registeredListingRepository.findByListingId("GANGNAM-202610-1234")).thenReturn(Optional.of(owner));

        service.delete(1L, "GANGNAM-202610-1234", null);

        verify(adminGuard, never()).requireAdmin(anyLong());
        verify(aiEngineClient).markListingDeleted("GANGNAM-202610-1234", "강남구");
    }

    @Test
    void 등록한_본인이_아니고_관리자도_아니면_FORBIDDEN() {
        RegisteredListing owner = RegisteredListing.of("GANGNAM-202610-1234", 1L, "강남구");
        when(registeredListingRepository.findByListingId("GANGNAM-202610-1234")).thenReturn(Optional.of(owner));
        doThrow(new CustomException(com.customhouse.global.error.ErrorCode.FORBIDDEN))
                .when(adminGuard).requireAdmin(2L);

        assertThatThrownBy(() -> service.delete(2L, "GANGNAM-202610-1234", null))
                .isInstanceOf(CustomException.class);
        verify(aiEngineClient, never()).markListingDeleted(any(), any());
    }

    @Test
    void 등록한_본인이_아니어도_관리자면_삭제된다() {
        RegisteredListing owner = RegisteredListing.of("GANGNAM-202610-1234", 1L, "강남구");
        when(registeredListingRepository.findByListingId("GANGNAM-202610-1234")).thenReturn(Optional.of(owner));
        // adminGuard.requireAdmin(99L)가 그냥 통과(관리자 확인됨)

        service.delete(99L, "GANGNAM-202610-1234", null);

        verify(adminGuard).requireAdmin(99L);
        verify(aiEngineClient).markListingDeleted("GANGNAM-202610-1234", "강남구");
    }

    @Test
    void 소유권_기록이_없으면_관리자_확인_후_요청받은_region으로_삭제한다() {
        when(registeredListingRepository.findByListingId("GANGNAM-202610-9999")).thenReturn(Optional.empty());

        service.delete(99L, "GANGNAM-202610-9999", "강남구");

        verify(adminGuard).requireAdmin(99L);
        verify(aiEngineClient).markListingDeleted("GANGNAM-202610-9999", "강남구");
    }

    @Test
    void 소유권_기록도_없고_region도_안_주면_VALIDATION_ERROR() {
        when(registeredListingRepository.findByListingId("GANGNAM-202610-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, "GANGNAM-202610-9999", null))
                .isInstanceOf(CustomException.class);
        verify(aiEngineClient, never()).markListingDeleted(any(), any());
    }

    @Test
    void 매물_상세는_AI_엔진_결과를_그대로_돌려준다() {
        Map<String, Object> raw = Map.of("listing_id", "GANGNAM-202610-1234");
        when(aiEngineClient.getListing("GANGNAM-202610-1234")).thenReturn(raw);

        assertThat(service.getDetail("GANGNAM-202610-1234")).isEqualTo(raw);
    }

    @Test
    void 매물_상세가_없으면_NOT_FOUND() {
        when(aiEngineClient.getListing("GANGNAM-202610-9999")).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail("GANGNAM-202610-9999")).isInstanceOf(CustomException.class);
    }

    @Test
    void 내_매물_목록은_소유한_매물만_최신순으로_가져오고_이미_지워진_매물은_건너뛴다() {
        RegisteredListing a = RegisteredListing.of("GANGNAM-202610-0001", 1L, "강남구");
        RegisteredListing b = RegisteredListing.of("GANGNAM-202610-0002", 1L, "강남구");
        when(registeredListingRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(a, b));
        when(aiEngineClient.getListing("GANGNAM-202610-0001")).thenReturn(Map.of("listing_id", "GANGNAM-202610-0001"));
        when(aiEngineClient.getListing("GANGNAM-202610-0002")).thenReturn(null); // 다른 경로로 이미 지워짐

        List<Map<String, Object>> result = service.listMine(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("listing_id", "GANGNAM-202610-0001");
    }

    @Test
    void 등록한_본인이면_수정된다() {
        RegisteredListing owner = RegisteredListing.of("GANGNAM-202610-1234", 1L, "강남구");
        when(registeredListingRepository.findByListingId("GANGNAM-202610-1234")).thenReturn(Optional.of(owner));
        ListingRegistrationRequest request = request();
        when(aiEngineClient.updateListing("GANGNAM-202610-1234", request))
                .thenReturn(new ListingRegistrationResponse("GANGNAM-202610-1234", "강남구"));

        ListingRegistrationResponse result = service.update(1L, "GANGNAM-202610-1234", request);

        assertThat(result.listingId()).isEqualTo("GANGNAM-202610-1234");
    }

    @Test
    void 등록한_본인이_아니면_관리자여도_수정은_FORBIDDEN() {
        RegisteredListing owner = RegisteredListing.of("GANGNAM-202610-1234", 1L, "강남구");
        when(registeredListingRepository.findByListingId("GANGNAM-202610-1234")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.update(99L, "GANGNAM-202610-1234", request()))
                .isInstanceOf(CustomException.class);
        verify(adminGuard, never()).requireAdmin(any());
        verify(aiEngineClient, never()).updateListing(any(), any());
    }

    @Test
    void 소유권_기록이_없는_매물은_수정_FORBIDDEN() {
        when(registeredListingRepository.findByListingId("GANGNAM-202610-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, "GANGNAM-202610-9999", request()))
                .isInstanceOf(CustomException.class);
        verify(aiEngineClient, never()).updateListing(any(), any());
    }

    @Test
    void 매물_수정에_성공하면_관심매물_담은_회원에게_가격_변동_알림_처리를_맡긴다() {
        when(registeredListingRepository.findByListingId("SEOCHO-202609-0001"))
                .thenReturn(Optional.of(RegisteredListing.of("SEOCHO-202609-0001", 1L, "서초구")));
        when(aiEngineClient.updateListing(eq("SEOCHO-202609-0001"), any()))
                .thenReturn(new ListingRegistrationResponse("SEOCHO-202609-0001", "서초구"));

        service.update(1L, "SEOCHO-202609-0001", request());

        verify(alertService).notifyPriceChange(eq("SEOCHO-202609-0001"), eq(5000), eq(80));
    }

    @Test
    void 알림_처리가_실패해도_매물_수정_결과는_그대로_돌려준다() {
        when(registeredListingRepository.findByListingId("SEOCHO-202609-0001"))
                .thenReturn(Optional.of(RegisteredListing.of("SEOCHO-202609-0001", 1L, "서초구")));
        when(aiEngineClient.updateListing(eq("SEOCHO-202609-0001"), any()))
                .thenReturn(new ListingRegistrationResponse("SEOCHO-202609-0001", "서초구"));
        doThrow(new RuntimeException("boom")).when(alertService).notifyPriceChange(any(), any(), any());

        assertThat(service.update(1L, "SEOCHO-202609-0001", request()).listingId()).isEqualTo("SEOCHO-202609-0001");
    }

    @Test
    void 본인_매물이_아니면_수정도_알림도_하지_않는다() {
        when(registeredListingRepository.findByListingId("SEOCHO-202609-0001"))
                .thenReturn(Optional.of(RegisteredListing.of("SEOCHO-202609-0001", 2L, "서초구")));

        assertThatThrownBy(() -> service.update(1L, "SEOCHO-202609-0001", request())).isInstanceOf(CustomException.class);
        verify(aiEngineClient, never()).updateListing(any(), any());
        verify(alertService, never()).notifyPriceChange(any(), any(), any());
    }
}
