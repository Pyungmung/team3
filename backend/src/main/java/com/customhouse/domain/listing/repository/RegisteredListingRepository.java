package com.customhouse.domain.listing.repository;

import com.customhouse.domain.listing.entity.RegisteredListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * [담당: 송귀성] 회원이 등록한 매물의 소유권 기록 JPA Repository
 */
public interface RegisteredListingRepository extends JpaRepository<RegisteredListing, Long> {

    Optional<RegisteredListing> findByListingId(String listingId);

    /** 마이페이지 "등록한 매물 관리" 탭 - 최근 등록한 것부터. */
    List<RegisteredListing> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
