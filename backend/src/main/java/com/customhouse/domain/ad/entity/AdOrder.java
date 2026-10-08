package com.customhouse.domain.ad.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 광고하기 주문 (2026-10-08). 토스 결제창은 페이지를 떠났다가 돌아오는 방식이라, 결제 전에 주문을 먼저 만들어 둔다.
 * - 이미 등록한 매물을 광고하는 주문: listingId에 그 매물번호가 들어 있다.
 * - 매물 등록 + 광고하기 주문: listingId는 비어 있고, 등록 폼 내용(JSON)이 pendingListingJson에 저장돼 있다가 결제 승인 후 서버가 매물을 등록한다.
 * 금액/노출 기간은 주문을 만들 때의 값(관리자 기타 설정)을 그대로 박아 둔다 - 결제 승인 때 프론트가 보낸 금액과 비교하는 기준이다.
 * 결제 기록 자체는 payments 테이블(Payment)에 같은 orderId로 남는다. 도메인 간 참조는 FK 없이 id만 쓴다.
 */
@Entity
@Table(name = "ad_orders", indexes = {
        @Index(name = "idx_ad_order_user", columnList = "user_id"),
        @Index(name = "idx_ad_order_listing", columnList = "listing_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdOrder extends BaseTimeEntity {

    public enum Status {
        /** 주문만 만들어졌고 아직 결제 승인 전 */
        PENDING,
        /** 결제 승인 처리 중 (같은 주문을 동시에 두 번 승인하지 못하게 하는 잠금 역할) */
        PROCESSING,
        /** 결제 승인 + 매물 등록 + 광고 접수까지 끝남 */
        DONE,
        /** 결제는 승인됐지만 매물 등록/광고 접수에 실패해 결제를 자동 취소함 (또는 승인 자체가 실패) */
        FAILED,
        /** 관리자가 환불(결제 취소)함 */
        CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 광고할 매물번호. 매물 등록 + 광고 주문은 결제 승인 후 등록되면서 채워진다. */
    @Column(name = "listing_id", length = 40)
    private String listingId;

    /** 매물 등록 + 광고 주문일 때 등록 폼 내용(ListingRegistrationRequest JSON). 등록이 끝나면 비운다. */
    @Column(name = "pending_listing_json", columnDefinition = "TEXT")
    private String pendingListingJson;

    /** 결제 금액(원) - 주문 시점의 광고 가격 */
    @Column(nullable = false)
    private Long amount;

    /** 이 주문으로 늘어나는 노출 기간(일) - 주문 시점의 값 */
    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    /** 실패/환불 사유 (관리자 화면 표시용) */
    @Column(name = "fail_reason", length = 300)
    private String failReason;

    public static AdOrder of(String orderId, Long userId, String listingId, String pendingListingJson, long amount, int periodDays) {
        AdOrder o = new AdOrder();
        o.orderId = orderId;
        o.userId = userId;
        o.listingId = listingId;
        o.pendingListingJson = pendingListingJson;
        o.amount = amount;
        o.periodDays = periodDays;
        return o;
    }

    /** 매물 등록 + 광고 주문에서 매물이 등록된 직후 매물번호를 기록해 둔다 (이후 광고 접수가 실패해도 어느 매물이 등록됐는지 남는다). */
    public void attachListing(String listingId) {
        this.listingId = listingId;
    }

    public void markProcessing(String paymentKey) {
        this.status = Status.PROCESSING;
        this.paymentKey = paymentKey;
    }

    /** 결제·등록·광고 접수가 모두 끝났다. 등록 폼 임시 저장 내용은 더 필요 없어 비운다. */
    public void markDone(String listingId) {
        this.status = Status.DONE;
        this.listingId = listingId;
        this.pendingListingJson = null;
        this.failReason = null;
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 300));
    }

    public void markCanceled(String reason) {
        this.status = Status.CANCELED;
        this.failReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 300));
    }
}
