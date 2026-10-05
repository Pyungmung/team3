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
 * 지금은 추천 매물 개수 상한 하나뿐이고, 앞으로 코드 수정 없이 관리자가 바꿔야 하는 설정이 생기면 여기에 컬럼을 추가한다.
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

    @Id
    private Long id;

    /** 추천 리포트에 보여줄 매물 개수 상한 (월세·전세 각각). 서버 부하(응답 크기/계산량)와 지도·목록에 보이는 매물 수를 함께 정한다. */
    private Integer recommendationLimit;

    public static AppSetting singleton() {
        AppSetting entity = new AppSetting();
        entity.id = SINGLETON_ID;
        entity.recommendationLimit = DEFAULT_RECOMMENDATION_LIMIT;
        return entity;
    }

    public void update(Integer recommendationLimit) {
        this.recommendationLimit = recommendationLimit;
    }
}
