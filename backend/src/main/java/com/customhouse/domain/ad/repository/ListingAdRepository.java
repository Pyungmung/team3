package com.customhouse.domain.ad.repository;

import com.customhouse.domain.ad.entity.ListingAd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * [담당: 송귀성] 광고하기 매물 JPA Repository
 */
public interface ListingAdRepository extends JpaRepository<ListingAd, Long> {

    Optional<ListingAd> findByListingId(String listingId);

    List<ListingAd> findByUserId(Long userId);

    List<ListingAd> findByListingIdIn(List<String> listingIds);

    /** 지금 광고 중인 매물번호 (노출 기간이 아직 안 끝난 것) - 추천 진단 요청에 실려 AI 엔진으로 간다. */
    @Query("select a.listingId from ListingAd a where a.expiresAt > :now order by a.id")
    List<String> findActiveListingIds(@Param("now") LocalDateTime now);

    /** 회원 탈퇴 시 그 회원의 광고 노출은 함께 정리한다 (주문/결제 이력은 남긴다). */
    @Transactional
    void deleteByUserId(Long userId);
}
