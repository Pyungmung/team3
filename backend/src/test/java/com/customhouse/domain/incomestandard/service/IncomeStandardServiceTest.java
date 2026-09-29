package com.customhouse.domain.incomestandard.service;

import com.customhouse.domain.incomestandard.dto.IncomeStandardRequest;
import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.incomestandard.entity.IncomeStandard;
import com.customhouse.domain.incomestandard.repository.IncomeStandardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 송귀성] 기준소득 통계 서비스 테스트. 저장소는 목으로 바꾸고 싱글톤 조회/저장 규칙을 확인한다.
 */
class IncomeStandardServiceTest {

    private IncomeStandardRepository repository;
    private IncomeStandardService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncomeStandardRepository.class);
        when(repository.saveAndFlush(any(IncomeStandard.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new IncomeStandardService(repository);
    }

    private IncomeStandardRequest request() {
        return new IncomeStandardRequest(15.8, 18.4, 18.3, 16.2, 19.4, 2024, "국토교통부,「주거실태조사」", 2_564_238L);
    }

    @Test
    void 저장된_값이_없으면_모두_빈_값으로_내려온다() {
        when(repository.findById(IncomeStandard.SINGLETON_ID)).thenReturn(Optional.empty());

        IncomeStandardResponse res = service.get();

        assertThat(res.rirMetroPercent()).isNull();
        assertThat(res.medianIncome100PercentMonthly()).isNull();
    }

    @Test
    void 저장하면_입력한_값이_그대로_돌아온다() {
        when(repository.findById(IncomeStandard.SINGLETON_ID)).thenReturn(Optional.empty());

        IncomeStandardResponse res = service.save(request());

        assertThat(res.rirOverallPercent()).isEqualTo(15.8);
        assertThat(res.rirMetroPercent()).isEqualTo(18.4);
        assertThat(res.rirLowPercent()).isEqualTo(18.3);
        assertThat(res.rirMidPercent()).isEqualTo(16.2);
        assertThat(res.rirHighPercent()).isEqualTo(19.4);
        assertThat(res.rirYear()).isEqualTo(2024);
        assertThat(res.rirSource()).isEqualTo("국토교통부,「주거실태조사」");
        assertThat(res.medianIncome100PercentMonthly()).isEqualTo(2_564_238L);
    }

    @Test
    void 이미_있는_행은_새로_만들지_않고_덮어쓴다() {
        IncomeStandard existing = IncomeStandard.singleton();
        when(repository.findById(IncomeStandard.SINGLETON_ID)).thenReturn(Optional.of(existing));

        service.save(request());

        ArgumentCaptor<IncomeStandard> captor = ArgumentCaptor.forClass(IncomeStandard.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getMedianIncome100PercentMonthly()).isEqualTo(2_564_238L);
    }

    @Test
    void 중위소득값은_1원_단위까지_정확히_저장된다() {
        when(repository.findById(IncomeStandard.SINGLETON_ID)).thenReturn(Optional.empty());

        IncomeStandardResponse res = service.save(new IncomeStandardRequest(
                null, null, null, null, null, null, null, 2_564_238L));

        assertThat(res.medianIncome100PercentMonthly()).isEqualTo(2_564_238L);
    }
}
