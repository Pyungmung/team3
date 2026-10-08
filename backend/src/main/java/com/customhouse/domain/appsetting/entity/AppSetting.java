package com.customhouse.domain.appsetting.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 송귀성] 기타 설정 (관리자 수정 > 기타 설정). 앱 전체가 공유하는 값이라 행이 하나뿐인 싱글톤 테이블이다(id=1 고정).
 * 추천 매물 개수 상한과, 광고하기(2026-10-08) 가격·노출 기간을 둔다. 앞으로 코드 수정 없이 관리자가 바꿔야 하는 설정이 생기면 여기에 컬럼을 추가한다.
 */
@Entity
@Table(name = "app_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting extends BaseTimeEntity {

    /** 싱글톤이라 항상 1L 고정 (자동 생성 안 함). */
    public static final Long SINGLETON_ID = 1L;

    /** 추천 개수 상한의 기본값 - 월세·전세 각각에 적용된다 (AI 엔진 listing_recommender.DEFAULT_TOP_N과 같은 값). */
    public static final int DEFAULT_RECOMMENDATION_LIMIT = 500;

    /** 광고하기 1회 결제 금액(원)과 노출 기간(일)의 기본값 - 관리자가 기타 설정에서 바꾸지 않았을 때(또는 기존 행에 값이 없을 때) 쓴다. */
    public static final int DEFAULT_AD_PRICE_WON = 1990;
    public static final int DEFAULT_AD_PERIOD_DAYS = 30;

    @Id
    private Long id;

    /** 추천 리포트에 보여줄 매물 개수 상한 (월세·전세 각각). 서버 부하(응답 크기/계산량)와 지도·목록에 보이는 매물 수를 함께 정한다. */
    private Integer recommendationLimit;

    /** 광고하기 1회 결제 금액(원). null이면 기본값(DEFAULT_AD_PRICE_WON). */
    private Integer adPriceWon;

    /** 광고하기 1회 결제 노출 기간(일). null이면 기본값(DEFAULT_AD_PERIOD_DAYS). */
    private Integer adPeriodDays;

    public static AppSetting singleton() {
        AppSetting entity = new AppSetting();
        entity.id = SINGLETON_ID;
        entity.recommendationLimit = DEFAULT_RECOMMENDATION_LIMIT;
        entity.adPriceWon = DEFAULT_AD_PRICE_WON;
        entity.adPeriodDays = DEFAULT_AD_PERIOD_DAYS;
        return entity;
    }

    /** 광고 가격/기간은 요청에 없으면(null) 지금 값을 그대로 둔다 - 예전 관리자 화면(추천 개수만 보내던)과 호환. */
    public void update(Integer recommendationLimit, Integer adPriceWon, Integer adPeriodDays) {
        this.recommendationLimit = recommendationLimit;
        if (adPriceWon != null) {
            this.adPriceWon = adPriceWon;
        }
        if (adPeriodDays != null) {
            this.adPeriodDays = adPeriodDays;
        }
    }
}
