package com.customhouse.domain.watchlist.repository;

import com.customhouse.domain.watchlist.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * [담당: 김시연] WatchList 도메인 - WatchlistItem(favorites) JPA Repository
 */
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserIdAndStatusNot(Long userId, WatchlistItem.WatchStatus excludedStatus);

    Optional<WatchlistItem> findByUserIdAndPropertyId(Long userId, Long propertyId);

    List<WatchlistItem> findByPropertyIdAndStatusNot(Long propertyId, WatchlistItem.WatchStatus excludedStatus);

    /** 회원 탈퇴 시 관심 매물을 함께 정리한다 (domain.user.service.UserService 참고). properties/registry_logs는
     * 매물 자체의 데이터라 여러 사용자가 공유하므로 건드리지 않는다. */
    void deleteByUserId(Long userId);
}
