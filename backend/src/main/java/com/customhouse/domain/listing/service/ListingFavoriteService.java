package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingFavoriteRequest;
import com.customhouse.domain.listing.dto.ListingFavoriteResponse;
import com.customhouse.domain.listing.dto.ListingReportStatus;
import com.customhouse.domain.listing.entity.ListingFavorite;
import com.customhouse.domain.listing.repository.ListingFavoriteRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
