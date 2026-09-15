package com.customhouse.domain.watchlist.repository;

import com.customhouse.domain.watchlist.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 김시연] WatchList 도메인 - Property JPA Repository
 */
public interface PropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByAddress(String address);
}
