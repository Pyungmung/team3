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
 * [담당: 송귀성] 추천 매물 - 허위매물 신고. 한 회원이 같은 매물을 두 번 신고하지 못하도록 (listing_id, user_id)를 유니크로 묶는다.
 * 매물은 CSV(더미 매물)에서 오므로 매물 테이블이 없고, CSV의 매물등록번호(예: SEOCHO-202609-0001)를 문자열 그대로 저장한다.
 * 회원은 FK 없이 id만 저장한다 (도메인 간 참조 규칙).
 */
@Entity
@Table(name = "listing_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_listing_report_user_listing", columnNames = {"listing_id", "user_id"}),
        indexes = @Index(name = "idx_listing_report_listing", columnList = "listing_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false, length = 40)
    private String listingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType;

    @Column(nullable = false, length = 1500)
    private String message;

    public static ListingReport of(String listingId, Long userId, String reportType, String message) {
        ListingReport report = new ListingReport();
        report.listingId = listingId;
        report.userId = userId;
        report.reportType = reportType;
        report.message = message;
        return report;
    }
}
