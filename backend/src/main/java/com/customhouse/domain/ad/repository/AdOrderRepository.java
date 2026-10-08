package com.customhouse.domain.ad.repository;

import com.customhouse.domain.ad.entity.AdOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * [담당: 송귀성] 광고하기 주문 JPA Repository
 */
public interface AdOrderRepository extends JpaRepository<AdOrder, Long> {

    Optional<AdOrder> findByOrderId(String orderId);

    /** 관리자 광고 현황 - 최근 주문부터 */
    List<AdOrder> findAllByOrderByIdDesc();
}
