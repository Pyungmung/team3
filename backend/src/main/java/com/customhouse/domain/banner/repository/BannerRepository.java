package com.customhouse.domain.banner.repository;

import com.customhouse.domain.banner.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [담당: 송귀성] 직접 배너 광고 JPA Repository
 */
public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<Banner> findAllByOrderBySortOrderAscIdAsc();
}
