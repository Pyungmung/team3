package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingFavoriteRequest;
import com.customhouse.domain.listing.dto.ListingFavoriteResponse;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.entity.ListingFavorite;
import com.customhouse.domain.listing.repository.ListingFavoriteRepository;
import com.customhouse.domain.recommendation.dto.RecommendRequest;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * [담당: 송귀성] 추천 매물 - 관심매물 등록/조회/삭제. 이미 담은 매물을 다시 담아도 오류 없이 넘어간다(멱등).
 */
@Service
@RequiredArgsConstructor
public class ListingFavoriteService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ListingReportStatus NO_REPORTS = new ListingReportStatus(0, false);

    private final ListingFavoriteRepository favoriteRepository;
    private final ListingReportService reportService;
    private final AiEngineClient aiEngineClient;

    public void add(Long userId, ListingFavoriteRequest request) {
        String snapshot = validSnapshot(request.snapshot());
        Optional<ListingFavorite> existing = favoriteRepository.findByUserIdAndListingId(userId, request.listingId());
        if (existing.isPresent()) {
            // 이미 담은 매물이라도 전체 정보가 함께 오면 갱신한다 (전체 정보 저장 이전에 담은 매물을 다시 담아 채울 수 있게)
            if (snapshot != null) {
                existing.get().updateSnapshot(snapshot);
                favoriteRepository.save(existing.get());
            }
            return;
        }
        try {
            favoriteRepository.saveAndFlush(ListingFavorite.of(userId, request.listingId(), request.address(),
                    request.region(), request.leaseType(), request.buildingName(), request.propertyType(),
                    request.unitLabel(), request.deposit(), request.monthlyRent(), request.maintenanceFee(), snapshot));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러 이미 담긴 경우 - 유니크 제약이 막아줬으니 성공으로 본다
        }
    }

    /** 비어 있으면 null, 값이 있으면 JSON 객체인지 확인한다 (화면에서 그대로 파싱해 쓰므로 깨진 값을 저장하지 않는다). */
    private static String validSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            if (!JSON.readTree(snapshot).isObject()) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "매물 정보 형식이 올바르지 않습니다.");
            }
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "매물 정보 형식이 올바르지 않습니다.");
        }
        return snapshot;
    }

    /**
     * 관심매물 새로고침 (2026-10-05) - 매물번호로 그 매물 1건을 사용자의 현재 진단 조건으로 다시 계산한 카드를 받아
     * 관심매물의 가격과 카드 전체 정보(snapshot)를 갱신한다. 가격이 바뀌어 간단 카드가 된 관심매물을 다시 전체 카드로 되돌린다.
     * 카드 JSON은 리포트에서 하트를 누를 때 저장하는 것과 같은 모양(_conversion_rate/_transport_type 포함)으로 저장한다.
     */
    @Transactional
    public ListingFavoriteResponse refresh(Long userId, String listingId, RecommendRequest condition) {
        ListingFavorite fav = favoriteRepository.findByUserIdAndListingId(userId, listingId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "관심 매물로 담은 매물이 아니에요."));

        Map<String, Object> result = aiEngineClient.requestListingRefresh(listingId, condition);
        if (result == null || !(result.get("listing") instanceof Map)) {
            throw new CustomException(ErrorCode.NOT_FOUND, "매물 정보를 불러오지 못했어요.");
        }
        ObjectNode card = JSON.valueToTree(result.get("listing"));
        if (result.get("deposit_conversion_rate") instanceof Number rate) {
            card.put("_conversion_rate", rate.doubleValue());
        }
        if (condition.transportType() != null) {
            card.put("_transport_type", condition.transportType());
        }
        fav.refreshFrom(intOrNull(card.get("listing_deposit")), intOrNull(card.get("listing_monthly_rent")),
                intOrNull(card.get("maintenance_fee")), JSON.writeValueAsString(card));
        favoriteRepository.save(fav);

        Map<String, ListingReportStatus> statuses = reportService.getStatuses(List.of(listingId));
        return ListingFavoriteResponse.of(fav, statuses.getOrDefault(listingId, NO_REPORTS));
    }

    private static Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    @Transactional
    public void remove(Long userId, String listingId) {
        favoriteRepository.deleteByUserIdAndListingId(userId, listingId);
    }

    /** 리포트 카드의 하트 상태를 그리기 위한 내 관심매물 번호 목록. */
    public List<String> getMyListingIds(Long userId) {
        return favoriteRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(ListingFavorite::getListingId)
                .toList();
    }

    public List<ListingFavoriteResponse> getMyFavorites(Long userId) {
        List<ListingFavorite> favorites = favoriteRepository.findByUserIdOrderByIdDesc(userId);
        Map<String, ListingReportStatus> statuses =
                reportService.getStatuses(favorites.stream().map(ListingFavorite::getListingId).toList());
        return favorites.stream()
                .map(fav -> ListingFavoriteResponse.of(fav, statuses.getOrDefault(fav.getListingId(), NO_REPORTS)))
                .toList();
    }
}
