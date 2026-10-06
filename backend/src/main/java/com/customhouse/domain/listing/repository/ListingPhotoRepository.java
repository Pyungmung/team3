package com.customhouse.domain.listing.repository;

import com.customhouse.domain.listing.entity.ListingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [담당: 송귀성] 매물 사진(DB 저장) JPA Repository. 기본 키가 파일 이름(UUID.확장자)이라 findById로 바로 읽는다.
 */
public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, String> {
}
