package com.customhouse.domain.watchlist.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [담당: 김시연] WatchList 도메인 - properties 테이블(매물 정보) 엔티티
 * 같은 주소(address)의 매물은 하나의 Property로 공유되고, 여러 사용자가 각자 WatchlistItem으로 관심 등록한다.
 */
@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 300)
    private String address;

    @Column(length = 50)
    private String region;        // 예: "관악구"

    @Column(nullable = false)
    private Integer deposit;      // 만원

    @Column(nullable = false)
    private Integer monthlyRent;  // 만원

    private Integer maintenanceFee; // 만원

    @Column(length = 500)
    private String sourceUrl;     // 매물 원본 출처(중개 플랫폼 등)

    /** 허위 매물 간편 신고 버튼 누적 카운트. 일정 횟수 이상이면 사용자에게 주의 표시. */
    @Builder.Default
    @Column(nullable = false)
    private int reportCount = 0;
}
