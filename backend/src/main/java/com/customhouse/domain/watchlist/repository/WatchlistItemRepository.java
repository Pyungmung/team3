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
}
