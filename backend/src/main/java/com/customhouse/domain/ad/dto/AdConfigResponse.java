package com.customhouse.domain.ad.dto;

/**
 * [담당: 송귀성] 광고하기 안내 - 현재 가격(원)/노출 기간(일, 기타 설정)과 토스 결제창에 쓰는 클라이언트 키(공개 키, .env의 TOSS_CLIENT_KEY).
 */
public record AdConfigResponse(int priceWon, int periodDays, String clientKey) {
}
