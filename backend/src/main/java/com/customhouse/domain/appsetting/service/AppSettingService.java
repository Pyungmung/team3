package com.customhouse.domain.appsetting.service;

import com.customhouse.domain.appsetting.dto.AppSettingRequest;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.entity.AppSetting;
import com.customhouse.domain.appsetting.repository.AppSettingRepository;
import com.customhouse.global.common.TtlCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 송귀성] 기타 설정(관리자 수정 > 기타 설정) 조회/저장. 행이 하나뿐인 싱글톤이라 삭제는 없다(수정만).
 * 행이 아직 없으면(시더 실행 전/테스트 환경) 기본값을 돌려주고, 저장 때 행을 만든다.
 */
@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository appSettingRepository;
    /** 진단 요청마다 읽던 값이라 5분 캐시한다(2026-10-06, 클라우드 DB 왕복이 느리다). 저장하면 비운다. */
    private final TtlCache<AppSettingResponse> cache = new TtlCache<>(5 * 60 * 1000L);

    public AppSettingResponse get() {
        return cache.get(this::load);
    }

    private AppSettingResponse load() {
        return appSettingRepository.findById(AppSetting.SINGLETON_ID)
                .map(AppSettingService::toResponse)
                .orElseGet(() -> new AppSettingResponse(AppSetting.DEFAULT_RECOMMENDATION_LIMIT, null));
    }

    @Transactional
    public AppSettingResponse save(AppSettingRequest request) {
        AppSetting entity = appSettingRepository.findById(AppSetting.SINGLETON_ID).orElseGet(AppSetting::singleton);
        entity.update(request.recommendationLimit());
        // saveAndFlush: 응답의 updatedAt이 방금 수정 시각이 되도록 즉시 flush (IncomeStandardService와 같은 이유)
        AppSettingResponse saved = toResponse(appSettingRepository.saveAndFlush(entity));
        cache.evictAfterCommit();
        return saved;
    }

    private static AppSettingResponse toResponse(AppSetting e) {
        Integer limit = e.getRecommendationLimit() != null ? e.getRecommendationLimit() : AppSetting.DEFAULT_RECOMMENDATION_LIMIT;
        return new AppSettingResponse(limit, e.getUpdatedAt());
    }
}
