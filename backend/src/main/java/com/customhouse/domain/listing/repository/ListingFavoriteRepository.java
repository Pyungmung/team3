package com.customhouse.domain.listing.repository;

import com.customhouse.domain.listing.entity.ListingFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * [담당: 송귀성] 추천 매물 - 관심매물 JPA Repository
 */
public interface ListingFavoriteRepository extends JpaRepository<ListingFavorite, Long> {

    boolean existsByUserIdAndListingId(Long userId, String listingId);

    Optional<ListingFavorite> findByUserIdAndListingId(Long userId, String listingId);

    List<ListingFavorite> findByUserIdOrderByIdDesc(Long userId);

    /** 이 매물을 관심매물로 담은 모든 회원 (가격 변동/허위매물 경고 알림 대상). */
    List<ListingFavorite> findByListingId(String listingId);

    void deleteByUserIdAndListingId(Long userId, String listingId);

    /** 회원 탈퇴 시 함께 정리한다 (domain.user.service.UserService 참고). */
    void deleteByUserId(Long userId);
}
