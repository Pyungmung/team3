package com.customhouse.domain.watchlist.service;

import com.customhouse.domain.mypage.repository.HousingConditionRepository;
import com.customhouse.domain.notification.service.NotificationService;
import com.customhouse.domain.watchlist.dto.AddWatchlistRequest;
import com.customhouse.domain.watchlist.dto.PriceRecheckRequest;
import com.customhouse.domain.watchlist.dto.WatchlistItemResponse;
import com.customhouse.domain.watchlist.entity.Property;
import com.customhouse.domain.watchlist.entity.RegistryLog;
import com.customhouse.domain.watchlist.entity.WatchlistItem;
import com.customhouse.domain.watchlist.repository.PropertyRepository;
import com.customhouse.domain.watchlist.repository.RegistryLogRepository;
import com.customhouse.domain.watchlist.repository.WatchlistItemRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [담당: 김시연] WatchList 도메인 - 관심 매물 등록·조회·삭제 및 변동 감지·알림 발행
 */
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final PropertyRepository propertyRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final RegistryLogRepository registryLogRepository;
    private final HousingConditionRepository housingConditionRepository;
    private final NotificationService notificationService;

    @Transactional
    public WatchlistItemResponse addToWatchlist(Long userId, AddWatchlistRequest request) {
        Property property = propertyRepository.findByAddress(request.address())
                .orElseGet(() -> propertyRepository.save(Property.builder()
                        .address(request.address())
                        .region(request.region())
                        .deposit(request.deposit())
                        .monthlyRent(request.monthlyRent())
                        .maintenanceFee(request.maintenanceFee())
                        .sourceUrl(request.sourceUrl())
                        .build()));

        WatchlistItem item = watchlistItemRepository.findByUserIdAndPropertyId(userId, property.getId())
                .map(existing -> {
                    existing.setStatus(WatchlistItem.WatchStatus.WATCHING);
                    return existing;
                })
                .orElseGet(() -> WatchlistItem.builder()
                        .userId(userId)
                        .propertyId(property.getId())
                        .build());

        watchlistItemRepository.save(item);
        return WatchlistItemResponse.of(item, property);
    }

    public List<WatchlistItemResponse> getMyWatchlist(Long userId) {
        return watchlistItemRepository.findByUserIdAndStatusNot(userId, WatchlistItem.WatchStatus.REMOVED).stream()
                .map(item -> {
                    Property property = propertyRepository.findById(item.getPropertyId())
                            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "매물 정보를 찾을 수 없습니다."));
                    return WatchlistItemResponse.of(item, property);
                })
                .toList();
    }

    @Transactional
    public void removeFromWatchlist(Long userId, Long watchlistItemId) {
        WatchlistItem item = getOwnedItem(userId, watchlistItemId);
        item.setStatus(WatchlistItem.WatchStatus.REMOVED);
        watchlistItemRepository.save(item);
    }

    /**
     * 매물 시세 재확인(변동 감지). 실제로는 배치/외부 API가 주기적으로 호출해야 하지만,
     * MVP에서는 관심 등록한 사용자가 프론트엔드 "가격 재확인" 버튼으로 직접 트리거한다.
     * 변동이 감지되면 RegistryLog에 이력을 남기고, 이 매물을 지켜보는 사용자 중
     * 알림 수신을 켠(mypage notificationEnabled=true) 사용자 전원에게 알림을 발행한다.
     */
    @Transactional
    public WatchlistItemResponse recheckPrice(Long userId, Long propertyId, PriceRecheckRequest request) {
        // 본인이 관심 등록한 매물인지 확인
        WatchlistItem myItem = watchlistItemRepository.findByUserIdAndPropertyId(userId, propertyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "관심 등록한 매물이 아닙니다."));

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "매물 정보를 찾을 수 없습니다."));

        boolean depositChanged = !request.deposit().equals(property.getDeposit());
        boolean rentChanged = !request.monthlyRent().equals(property.getMonthlyRent());

        if (depositChanged || rentChanged) {
            String description = "보증금 %d → %d만원, 월세 %d → %d만원".formatted(
                    property.getDeposit(), request.deposit(), property.getMonthlyRent(), request.monthlyRent());

            registryLogRepository.save(RegistryLog.builder()
                    .propertyId(propertyId)
                    .changeType(depositChanged && rentChanged ? "보증금·월세 변경" : depositChanged ? "보증금 변경" : "월세 변경")
                    .description(description)
                    .detectedAt(LocalDateTime.now())
                    .build());

            property.setDeposit(request.deposit());
            property.setMonthlyRent(request.monthlyRent());
            propertyRepository.save(property);

            notifyWatchersOfChange(propertyId, property.getAddress(), description);
        }

        return WatchlistItemResponse.of(myItem, property);
    }

    /** 허위 매물 간편 신고. 신고 누적 시 지켜보는 사용자들에게 주의 알림을 보낸다. */
    @Transactional
    public void report(Long userId, Long propertyId) {
        // 관심 등록 여부와 무관하게 신고는 가능하되, 매물 존재는 확인한다.
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "매물 정보를 찾을 수 없습니다."));

        property.setReportCount(property.getReportCount() + 1);
        propertyRepository.save(property);

        if (property.getReportCount() == 3) {
            notifyWatchersOfChange(propertyId, property.getAddress(),
                    "다른 사용자들의 신고가 누적되어 허위 매물 의심 표시가 추가되었습니다.");
        }
    }

    private void notifyWatchersOfChange(Long propertyId, String address, String description) {
        List<WatchlistItem> watchers = watchlistItemRepository
                .findByPropertyIdAndStatusNot(propertyId, WatchlistItem.WatchStatus.REMOVED);

        for (WatchlistItem watcher : watchers) {
            boolean notificationEnabled = housingConditionRepository.findByUserId(watcher.getUserId())
                    .map(condition -> condition.isNotificationEnabled())
                    .orElse(false);

            if (!notificationEnabled) {
                continue;
            }

            watcher.setStatus(WatchlistItem.WatchStatus.CHANGED);
            watchlistItemRepository.save(watcher);

            notificationService.notify(
                    watcher.getUserId(),
                    "관심 매물 변동 알림",
                    "[" + address + "] " + description
            );
        }
    }

    private WatchlistItem getOwnedItem(Long userId, Long watchlistItemId) {
        WatchlistItem item = watchlistItemRepository.findById(watchlistItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "관심 매물을 찾을 수 없습니다."));

        if (!item.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인의 관심 매물만 관리할 수 있습니다.");
        }
        return item;
    }
}
