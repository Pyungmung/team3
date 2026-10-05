package com.customhouse.domain.watchlist.repository;

import com.customhouse.domain.watchlist.entity.RegistryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [담당: 김시연] WatchList 도메인 - RegistryLog JPA Repository
 */
public interface RegistryLogRepository extends JpaRepository<RegistryLog, Long> {

    List<RegistryLog> findByPropertyIdOrderByDetectedAtDesc(Long propertyId);
}
