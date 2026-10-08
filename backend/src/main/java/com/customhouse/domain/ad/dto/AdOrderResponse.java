package com.customhouse.domain.ad.dto;

/**
 * [담당: 송귀성] 광고하기 주문 생성 결과 - 프론트가 이 값으로 토스 결제창(requestPayment)을 연다.
 */
public record AdOrderResponse(String orderId, String orderName, long amount, int periodDays, String clientKey) {
}
