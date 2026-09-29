package com.customhouse.domain.incomestandard.service;

import com.customhouse.domain.incomestandard.entity.IncomeStandard;
import com.customhouse.domain.incomestandard.repository.IncomeStandardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [담당: 송귀성] 기준소득 통계 초기 시드. docs/RIR.csv(2024년 국토교통부 주거실태조사)와
 * docs/housing_policy_list.csv(15열, 기준중위소득값)에 있던 실제 값으로 서버 최초 기동 시 1행을 만들어둔다.
 * DevAdminSeeder와 달리 비밀번호 같은 민감정보가 아니라 공개 통계라 운영(prod) 환경에도 필요하므로 프로필 제한이 없다.
 * 이미 행이 있으면(관리자가 이미 저장했거나 이전에 시드했으면) 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncomeStandardSeeder implements ApplicationRunner {

    private final IncomeStandardRepository incomeStandardRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (incomeStandardRepository.existsById(IncomeStandard.SINGLETON_ID)) {
            return;
        }
        IncomeStandard seed = IncomeStandard.singleton();
        seed.update(15.8, 18.4, 18.3, 16.2, 19.4, 2024, "국토교통부,「주거실태조사」", 2_564_238L);
        incomeStandardRepository.save(seed);
        log.info("기준소득 통계 초기값을 시드했습니다: 수도권 RIR {}%, 기준중위소득 {}원", seed.getRirMetroPercent(), seed.getMedianIncome100PercentMonthly());
    }
}
