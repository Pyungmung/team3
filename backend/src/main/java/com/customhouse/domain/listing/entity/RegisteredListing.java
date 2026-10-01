package com.customhouse.domain.listing.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 회원이 직접 등록한 매물의 "소유권" 기록. 매물 내용 자체는 CSV(AI 엔진)에 저장되고,
 * 여기는 "이 매물등록번호를 누가 등록했는지"만 담아 삭제 권한 판정에 쓴다 (ListingReport와 같은 이유로
 * 매물 테이블이 없어 listing_id를 문자열 그대로 저장한다. 회원은 FK 없이 id만 저장 - 도메인 간 참조 규칙).
 * region은 AI 엔진에 삭제(상태 변경) 요청을 보낼 때 어느 자치구 CSV인지 알려주는 데 쓴다.
 */
@Entity
@Table(name = "registered_listings",
        uniqueConstraints = @UniqueConstraint(name = "uk_registered_listing_listing_id", columnNames = "listing_id"),
        indexes = @Index(name = "idx_registered_listing_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegisteredListing extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false, length = 40)
    private String listingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String region;

    public static RegisteredListing of(String listingId, Long userId, String region) {
        RegisteredListing entity = new RegisteredListing();
        entity.listingId = listingId;
        entity.userId = userId;
        entity.region = region;
        return entity;
    }
}
