package com.customhouse.domain.incomestandard.service;

import com.customhouse.domain.incomestandard.dto.IncomeStandardRequest;
import com.customhouse.domain.incomestandard.dto.IncomeStandardResponse;
import com.customhouse.domain.incomestandard.entity.IncomeStandard;
import com.customhouse.domain.incomestandard.repository.IncomeStandardRepository;
import com.customhouse.global.common.TtlCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 송귀성] 기준소득 통계(관리자 수정 > 기준소득관리) 조회/저장. 행이 하나뿐인 싱글톤이라 삭제는 없다(수정만).
 * IncomeStandardSeeder가 서버 최초 기동 때 실제 값으로 1행을 미리 만들어두므로, get()이 빈 값을 돌려주는 상황은
 * 시더 실행 전(찰나) 또는 시더를 건너뛴 테스트 환경뿐이다 - 그런 경우에도 방어적으로 빈 응답을 돌려준다(예외를 던지지 않는다).
 */
@Service
@RequiredArgsConstructor
public class IncomeStandardService {

    private final IncomeStandardRepository incomeStandardRepository;
    /** 진단 요청마다 읽던 값이라 5분 캐시한다(2026-10-06, 클라우드 DB 왕복이 느리다). 저장하면 비운다. */
    private final TtlCache<IncomeStandardResponse> cache = new TtlCache<>(5 * 60 * 1000L);

    public IncomeStandardResponse get() {
        return cache.get(this::load);
    }

    private IncomeStandardResponse load() {
        return incomeStandardRepository.findById(IncomeStandard.SINGLETON_ID)
                .map(IncomeStandardService::toResponse)
                .orElseGet(() -> new IncomeStandardResponse(null, null, null, null, null, null, null, null, null));
    }

    @Transactional
    public IncomeStandardResponse save(IncomeStandardRequest request) {
        IncomeStandard entity = incomeStandardRepository.findById(IncomeStandard.SINGLETON_ID)
                .orElseGet(IncomeStandard::singleton);
        entity.update(request.rirOverallPercent(), request.rirMetroPercent(), request.rirLowPercent(),
                request.rirMidPercent(), request.rirHighPercent(), request.rirYear(), request.rirSource(),
                request.medianIncome100PercentMonthly());
        // saveAndFlush로 즉시 flush해야 @LastModifiedDate(updatedAt)이 이 트랜잭션 안에서 바로 반영된다.
        // save()만 쓰면 실제 UPDATE(및 감사 리스너)가 트랜잭션 커밋 시점까지 미뤄져, 이 응답의 updatedAt이
        // 방금 한 수정이 아니라 이전 값으로 내려가는 문제가 있었다(관리자 화면 "마지막 수정" 표시가 틀리게 됨).
        IncomeStandard saved = incomeStandardRepository.saveAndFlush(entity);
        cache.evictAfterCommit();
        return toResponse(saved);
    }

    private static IncomeStandardResponse toResponse(IncomeStandard e) {
        return new IncomeStandardResponse(e.getRirOverallPercent(), e.getRirMetroPercent(), e.getRirLowPercent(),
                e.getRirMidPercent(), e.getRirHighPercent(), e.getRirYear(), e.getRirSource(),
                e.getMedianIncome100PercentMonthly(), e.getUpdatedAt());
    }
}
