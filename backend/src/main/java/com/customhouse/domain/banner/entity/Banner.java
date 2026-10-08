package com.customhouse.domain.banner.entity;

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

import java.time.LocalDate;

/**
 * [담당: 송귀성] 직접 배너 광고 (2026-10-08). 관리자가 사진·링크·노출 조건을 올려 두면, 광고 자리(프론트 data-ad-slot)가 방문자 조건에
 * 맞는 배너를 무작위로 하나 보여 준다. 조건 판정은 프론트(ad-targeting.js)가 하고 서버는 "지금 노출 가능한 배너 목록"만 내려준다.
 * 조건 값(월세 성향/직장 자치구/이사 일정/자리 이름)은 비워 두면 제한 없음이다. 노출·클릭 횟수는 집계하지 않는다. FK 없이 단독 테이블.
 */
@Entity
@Table(name = "banners", indexes = @Index(name = "idx_banner_active", columnList = "active"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseTimeEntity {

    public enum Category { MOVING, INTERNET, CLEANING, GROCERY, ETC }

    /** 월세 성향: ALL=누구에게나, SAVING=절약형(최대 월세 <= 적정 월세 상한), PREMIUM=프리미엄형(최대 월세 > 상한) */
    public enum Segment { ALL, SAVING, PREMIUM }

    /** 이사 일정 조건: ANY=제한 없음, IMMEDIATE=1개월 이내만, WITHIN_3M=3개월 이내까지(1개월 이내 포함) */
    public enum MoveWithin { ANY, IMMEDIATE, WITHIN_3M }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", nullable = false, length = 1000)
    private String linkUrl;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Segment segment;

    /** 직장 자치구 조건 (쉼표로 구분, 비면 제한 없음) */
    @Column(length = 500)
    private String regions;

    @Enumerated(EnumType.STRING)
    @Column(name = "move_within", nullable = false, length = 20)
    private MoveWithin moveWithin;

    /** 노출할 광고 자리 이름 (쉼표로 구분, 비면 모든 자리) */
    @Column(length = 300)
    private String slots;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static Banner create() {
        return new Banner();
    }

    public void apply(String title, Category category, String imageUrl, String linkUrl, String altText, Segment segment, String regions,
                      MoveWithin moveWithin, String slots, LocalDate startsOn, LocalDate endsOn, boolean active, int sortOrder) {
        this.title = title;
        this.category = category;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.altText = altText;
        this.segment = segment;
        this.regions = regions;
        this.moveWithin = moveWithin;
        this.slots = slots;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    /** 오늘 노출 기간 안인가 (시작일/종료일은 그날 포함, 비어 있으면 제한 없음) */
    public boolean isInPeriod(LocalDate today) {
        return (startsOn == null || !today.isBefore(startsOn)) && (endsOn == null || !today.isAfter(endsOn));
    }
}
