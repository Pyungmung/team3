package com.customhouse.domain.listing.repository;

import com.customhouse.domain.listing.entity.ListingReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * [담당: 송귀성] 추천 매물 - 허위매물 신고 JPA Repository
 */
public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    boolean existsByListingIdAndUserId(String listingId, Long userId);

    long countByListingId(String listingId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    /** 여러 매물의 신고 수를 한 번에 센다. 결과 행: [listingId, count] (신고가 없는 매물은 나오지 않는다). */
    @Query("select r.listingId, count(r) from ListingReport r where r.listingId in :listingIds group by r.listingId")
    List<Object[]> countByListingIds(@Param("listingIds") Collection<String> listingIds);
}
