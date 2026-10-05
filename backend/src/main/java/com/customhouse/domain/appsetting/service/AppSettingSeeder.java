package com.customhouse.domain.appsetting.service;

import com.customhouse.domain.appsetting.entity.AppSetting;
import com.customhouse.domain.appsetting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [담당: 송귀성] 기타 설정 초기 시드. 서버 최초 기동 시 기본값(추천 개수 상한 500)으로 1행을 만들어둔다.
 * 이미 행이 있으면(관리자가 이미 저장했으면) 건드리지 않는다. 비밀 값이 아니라 프로필 제한은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppSettingSeeder implements ApplicationRunner {

    private final AppSettingRepository appSettingRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (appSettingRepository.existsById(AppSetting.SINGLETON_ID)) {
            return;
        }
        appSettingRepository.save(AppSetting.singleton());
        log.info("기타 설정 초기값을 시드했습니다: 추천 개수 상한 {}", AppSetting.DEFAULT_RECOMMENDATION_LIMIT);
    }
}
