package com.customhouse.domain.ad.entity;

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
 * [담당: 송귀성] 광고하기 매물 (2026-10-08). 매물 하나당 한 행이고, 매물 내용은 복사하지 않고 매물번호만 들고 있다
 * (매물을 고치면 광고에도 그대로 반영되고, 매물 등록 상태는 광고와 별개로 유지된다).
 * 광고 중 = expiresAt이 지금보다 뒤. 이미 광고 중인 매물을 다시 결제하면 expiresAt에 기간이 더해진다(연장).
 */
@Entity
@Table(name = "listing_ads",
        uniqueConstraints = @UniqueConstraint(name = "uk_listing_ad_listing", columnNames = "listing_id"),
        indexes = {
                @Index(name = "idx_listing_ad_user", columnList = "user_id"),
                @Index(name = "idx_listing_ad_expires", columnList = "expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingAd extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false, length = 40)
    private String listingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 마지막으로 이 광고를 늘린 주문 */
    @Column(name = "last_order_id", length = 64)
    private String lastOrderId;

    public static ListingAd start(String listingId, Long userId, LocalDateTime now, int periodDays, String orderId) {
        ListingAd ad = new ListingAd();
        ad.listingId = listingId;
        ad.userId = userId;
        ad.startedAt = now;
        ad.expiresAt = now.plusDays(periodDays);
        ad.lastOrderId = orderId;
        return ad;
    }

    public boolean isActive(LocalDateTime now) {
        return expiresAt.isAfter(now);
    }

    /** 결제로 노출 기간을 늘린다: 광고 중이면 만료일에 이어서, 이미 끝났으면 지금부터 새로 시작한다. */
    public void extend(LocalDateTime now, int periodDays, String orderId) {
        if (isActive(now)) {
            this.expiresAt = expiresAt.plusDays(periodDays);
        } else {
            this.startedAt = now;
            this.expiresAt = now.plusDays(periodDays);
        }
        this.lastOrderId = orderId;
    }

    /** 환불: 그 주문이 늘려 준 기간만큼 줄인다(지금보다 앞으로는 줄이지 않는다 = 최대 즉시 종료). */
    public void shorten(LocalDateTime now, int periodDays) {
        LocalDateTime reduced = expiresAt.minusDays(periodDays);
        this.expiresAt = reduced.isBefore(now) ? now : reduced;
    }
}
