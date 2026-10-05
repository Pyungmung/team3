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

import java.time.LocalDateTime;

/**
 * [담당: 송귀성] 추천 매물 - 관심매물(마이페이지 "관심 매물"에 함께 표시).
 * 추천 매물은 CSV에서 오므로 매물 테이블이 없다. 목록 화면을 그리려고 담을 당시의 주소/가격을 스냅샷으로 함께 저장하고,
 * 매물은 매물등록번호(listing_id)로 식별한다. (user_id, listing_id) 유니크라 중복 등록이 DB 차원에서 막힌다.
 * 기존 관심 매물(favorites/properties)과는 별개 테이블이다 (properties.address가 UNIQUE라 한 주소를 공유하는 더미 매물을 담을 수 없다).
 */
@Entity
@Table(name = "listing_favorites",
        uniqueConstraints = @UniqueConstraint(name = "uk_listing_fav_user_listing", columnNames = {"user_id", "listing_id"}),
        indexes = @Index(name = "idx_listing_fav_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingFavorite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "listing_id", nullable = false, length = 40)
    private String listingId;

    @Column(length = 300)
    private String address;

    @Column(length = 30)
    private String region;

    /** 월세 / 전세 */
    @Column(name = "lease_type", length = 10)
    private String leaseType;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @Column(name = "property_type", length = 30)
    private String propertyType;

    @Column(name = "unit_label", length = 60)
    private String unitLabel;

    /** 보증금(만원) */
    private Integer deposit;

    /** 월세(만원) */
    @Column(name = "monthly_rent")
    private Integer monthlyRent;

    /** 관리비(만원) */
    @Column(name = "maintenance_fee")
    private Integer maintenanceFee;

    /**
     * 담을 당시 추천 리포트 카드의 전체 매물 정보(JSON 문자열). 마이페이지에서 리포트와 같은 카드로 다시 그리려고 통째로 저장한다.
     * 위의 address/deposit 등은 이 값이 없는 예전 관심매물의 간단 표시용이다. 서버는 내용을 해석하지 않고 JSON 형식만 확인한다.
     */
    @Column(columnDefinition = "TEXT")
    private String snapshot;

    /** 가격이 바뀌기 직전의 보증금(만원) - 알림함/카드의 "옛 가격 -> 새 가격" 표시용. 가격 변동이 없었으면 null. */
    @Column(name = "previous_deposit")
    private Integer previousDeposit;

    /** 가격이 바뀌기 직전의 월세(만원). */
    @Column(name = "previous_monthly_rent")
    private Integer previousMonthlyRent;

    /** 마지막으로 가격 변동이 감지된 시각. */
    @Column(name = "price_changed_at")
    private LocalDateTime priceChangedAt;

    public void updateSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * 관심매물 새로고침 결과를 반영한다 (2026-10-05) - 새 가격/관리비와, 새 가격 기준으로 다시 계산한 카드 전체(snapshot).
     * 가격 변동 이력(previous*, priceChangedAt)은 그대로 둔다.
     */
    public void refreshFrom(Integer deposit, Integer monthlyRent, Integer maintenanceFee, String snapshot) {
        this.deposit = deposit;
        this.monthlyRent = monthlyRent;
        this.maintenanceFee = maintenanceFee;
        this.snapshot = snapshot;
    }

    /**
     * 매물 가격이 바뀌었을 때 호출한다 (2026-10-05). 옛 가격을 남기고 새 가격으로 바꾼다.
     * 담을 당시 카드 전체(snapshot)에는 실질주거비 같은 계산값이 옛 가격 기준으로 들어 있어서 함께 비운다 -
     * 비우면 화면이 새 가격이 반영된 간단 카드(위 deposit/monthlyRent)로 보여준다.
     */
    public void applyPriceChange(Integer newDeposit, Integer newMonthlyRent) {
        this.previousDeposit = this.deposit;
        this.previousMonthlyRent = this.monthlyRent;
        this.deposit = newDeposit;
        this.monthlyRent = newMonthlyRent;
        this.snapshot = null;
        this.priceChangedAt = LocalDateTime.now();
    }

    public static ListingFavorite of(Long userId, String listingId, String address, String region, String leaseType,
                                     String buildingName, String propertyType, String unitLabel,
                                     Integer deposit, Integer monthlyRent, Integer maintenanceFee, String snapshot) {
        ListingFavorite fav = new ListingFavorite();
        fav.userId = userId;
        fav.listingId = listingId;
        fav.address = address;
        fav.region = region;
        fav.leaseType = leaseType;
        fav.buildingName = buildingName;
        fav.propertyType = propertyType;
        fav.unitLabel = unitLabel;
        fav.deposit = deposit;
        fav.monthlyRent = monthlyRent;
        fav.maintenanceFee = maintenanceFee;
        fav.snapshot = snapshot;
        return fav;
    }
}
