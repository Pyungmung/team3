package com.customhouse.domain.listing.service;

import com.customhouse.domain.listing.dto.ListingRegistrationRequest;
import com.customhouse.domain.listing.dto.ListingRegistrationResponse;
import com.customhouse.domain.listing.entity.RegisteredListing;
import com.customhouse.domain.listing.repository.RegisteredListingRepository;
import com.customhouse.domain.recommendation.service.AiEngineClient;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [담당: 송귀성] 회원 매물 등록/조회/수정/삭제. 매물 내용은 AI 엔진(CSV)이 쓰고, 여기는 "누가
 * 등록했는지"와 함께 매물 내용 원본(CSV 행 55컬럼 JSON, rowJson)을 RegisteredListing에 남긴다 - 권한 판정에 쓰고,
 * 무료 서버가 재시작돼 AI 엔진의 CSV가 처음 상태로 돌아가도 엔진이 켜질 때 이 값으로 되살린다(2026-10-06).
 * 수정은 등록한 본인만(관리자 예외 없음), 삭제는 본인 또는 관리자(더미 매물 포함 전체)가 할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingRegistrationService {

    private final AiEngineClient aiEngineClient;
    private final RegisteredListingRepository registeredListingRepository;
    private final AdminGuard adminGuard;
    private final ListingAlertService alertService;

    private static final ObjectMapper JSON = new ObjectMapper();
    /** CSV 헤더 중 매물 상태 컬럼 이름 (listing_schema.LISTING_FIELDS의 "매물상태"). */
    private static final String STATUS_COLUMN = "매물상태";

    public ListingRegistrationResponse register(Long userId, ListingRegistrationRequest request) {
        ListingRegistrationResponse result = aiEngineClient.registerListing(request);
        registeredListingRepository.save(RegisteredListing.of(result.listingId(), userId, result.region(), toJson(result.row())));
        return result;
    }

    /** 매물 1건의 원본 정보 (마이페이지 상세/수정 폼 프리필 공용). 없으면 NOT_FOUND. */
    public Map<String, Object> getDetail(String listingId) {
        Map<String, Object> listing = aiEngineClient.getListing(listingId);
        if (listing == null) {
            throw new CustomException(ErrorCode.NOT_FOUND, "매물을 찾을 수 없어요.");
        }
        return listing;
    }

    /** 마이페이지 "등록한 매물 관리" 탭 - 내가 등록한 매물 전부(최근 등록 순). AI 엔진에서 이미 지워진
     * 매물(404)은 조용히 건너뛴다 - 다른 경로로 지워졌을 수 있다. */
    public List<Map<String, Object>> listMine(Long userId) {
        return registeredListingRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(owned -> aiEngineClient.getListing(owned.getListingId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 등록한 본인만 수정할 수 있다(관리자 예외 없음 - 사용자 요청사항). */
    public ListingRegistrationResponse update(Long userId, String listingId, ListingRegistrationRequest request) {
        RegisteredListing owner = registeredListingRepository.findByListingId(listingId).orElse(null);
        if (owner == null || !owner.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인이 등록한 매물만 수정할 수 있어요.");
        }
        ListingRegistrationResponse response = aiEngineClient.updateListing(listingId, request);
        if (response.row() != null) {
            owner.updateRowJson(toJson(response.row()));
            registeredListingRepository.save(owner);
        }
        // 가격(보증금/월세)이 바뀌었으면 이 매물을 관심매물로 담은 회원에게 알린다. 알림 처리 실패가 매물 수정을 막으면 안 된다.
        try {
            alertService.notifyPriceChange(listingId, request.deposit(), request.monthlyRent());
        } catch (RuntimeException e) {
            log.warn("매물 가격 변동 알림 처리에 실패했습니다 (매물: {}): {}", listingId, e.toString());
        }
        return response;
    }

    /** 등록한 본인이면 통과, 아니면 관리자여야 한다(AdminGuard가 아니면 FORBIDDEN을 던진다). */
    public void delete(Long userId, String listingId, String region) {
        RegisteredListing owner = registeredListingRepository.findByListingId(listingId).orElse(null);
        boolean isOwner = owner != null && owner.getUserId().equals(userId);
        if (!isOwner) {
            adminGuard.requireAdmin(userId);
        }

        String effectiveRegion = owner != null ? owner.getRegion() : region;
        if (effectiveRegion == null || effectiveRegion.isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "매물의 자치구 정보를 알 수 없어 삭제할 수 없습니다.");
        }
        aiEngineClient.markListingDeleted(listingId, effectiveRegion);
        // DB에 저장해 둔 매물 원본도 삭제 상태로 바꾼다 (엔진이 다시 켜져 복원될 때 삭제된 채로 돌아오게)
        if (owner != null && owner.getRowJson() != null) {
            owner.updateRowJson(withDeletedStatus(owner.getRowJson()));
            registeredListingRepository.save(owner);
        }
        // 이 매물을 관심매물로 담은 회원에게 삭제를 알린다. 알림 처리 실패가 삭제를 되돌리거나 막으면 안 된다.
        try {
            alertService.notifyDeleted(listingId, userId);
        } catch (RuntimeException e) {
            log.warn("매물 삭제 알림 처리에 실패했습니다 (매물: {}): {}", listingId, e.toString());
        }
    }

    private static String toJson(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(row);
    }

    /** 저장된 행 JSON의 매물상태를 "삭제됨"으로 바꾼다. 깨진 JSON이면 그대로 둔다. */
    static String withDeletedStatus(String rowJson) {
        try {
            ObjectNode node = (ObjectNode) JSON.readTree(rowJson);
            node.put(STATUS_COLUMN, "삭제됨");
            return JSON.writeValueAsString(node);
        } catch (RuntimeException e) {
            log.warn("저장된 매물 원본의 삭제 상태 반영에 실패했습니다: {}", e.toString());
            return rowJson;
        }
    }
}
