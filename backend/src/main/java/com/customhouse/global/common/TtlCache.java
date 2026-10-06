package com.customhouse.global.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * [담당: 송귀성] 값 하나를 일정 시간(TTL) 메모리에 들고 있는 아주 작은 캐시 (2026-10-06).
 * 진단 요청마다 DB에서 읽던 관리자 설정(대출 조건/기준소득/기타 설정)을 매번 읽지 않게 하려고 만들었다 - 클라우드 DB는 쿼리 1번이 0.6초쯤
 * 걸려서 요청마다 8~10번 읽으면 진단이 6~9초가 됐다(AI 엔진 계산은 0.3초).
 * 관리자가 값을 저장/삭제할 때는 evictAfterCommit()으로 비워서, 저장 직후 다음 요청부터 새 값이 쓰이게 한다(TTL은 안전장치).
 * 캐시에 담는 값은 바뀌지 않는 객체(record/불변 List)여야 한다. 서버가 한 대라는 전제다 - 여러 대로 늘리면 다른 서버는 TTL이 지나야 새 값을 본다.
 */
public class TtlCache<T> {

    private final long ttlMillis;
    private volatile T value;
    private volatile long loadedAt;

    public TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public T get(Supplier<T> loader) {
        T cached = value;
        if (cached != null && System.currentTimeMillis() - loadedAt < ttlMillis) {
            return cached;
        }
        synchronized (this) {
            cached = value;
            if (cached != null && System.currentTimeMillis() - loadedAt < ttlMillis) {
                return cached;
            }
            T loaded = loader.get();
            value = loaded;
            loadedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    public void evict() {
        value = null;
    }

    /** 저장/삭제하는 트랜잭션이 커밋된 뒤에 비운다 (커밋 전에 비우면 그 사이 읽은 옛 값이 다시 캐시된다). 트랜잭션이 없으면 바로 비운다. */
    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict();
                }
            });
        } else {
            evict();
        }
    }
}
