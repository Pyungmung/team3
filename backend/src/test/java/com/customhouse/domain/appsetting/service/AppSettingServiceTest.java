package com.customhouse.domain.appsetting.service;

import com.customhouse.domain.appsetting.dto.AppSettingRequest;
import com.customhouse.domain.appsetting.dto.AppSettingResponse;
import com.customhouse.domain.appsetting.entity.AppSetting;
import com.customhouse.domain.appsetting.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 기타 설정 서비스 테스트. 저장소는 목으로 바꾸고 싱글톤 조회/저장 규칙(없으면 기본값 500)을 확인한다.
 */
class AppSettingServiceTest {

    private AppSettingRepository repository;
    private AppSettingService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        when(repository.saveAndFlush(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AppSettingService(repository);
    }

    @Test
    void 저장된_행이_없으면_기본값_500이_내려온다() {
        when(repository.findById(AppSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThat(service.get().recommendationLimit()).isEqualTo(500);
    }

    @Test
    void 저장하면_입력한_값이_그대로_돌아오고_다음_조회에도_쓰인다() {
        AppSetting row = AppSetting.singleton();
        when(repository.findById(AppSetting.SINGLETON_ID)).thenReturn(Optional.of(row));

        AppSettingResponse saved = service.save(new AppSettingRequest(300));

        assertThat(saved.recommendationLimit()).isEqualTo(300);
        assertThat(service.get().recommendationLimit()).isEqualTo(300);
    }

    @Test
    void 행이_없을_때_저장하면_새로_만든다() {
        when(repository.findById(AppSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThat(service.save(new AppSettingRequest(800)).recommendationLimit()).isEqualTo(800);
    }
}
