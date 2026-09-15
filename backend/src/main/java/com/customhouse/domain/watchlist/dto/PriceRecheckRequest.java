package com.customhouse.domain.watchlist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 김시연] WatchList 도메인 - 매물 시세 재확인(변동 감지) 요청 DTO
 * 실제 서비스에서는 외부 API/크롤링 배치가 주기적으로 최신 시세를 가져와 이 로직을 호출하지만,
 * MVP 단계에서는 프론트엔드의 "가격 재확인" 버튼으로 사용자가 직접 트리거한다.
 */
public record PriceRecheckRequest(
        @NotNull(message = "deposit은 필수입니다.")
        @Min(value = 0, message = "deposit은 0 이상이어야 합니다.")
        Integer deposit,

        @NotNull(message = "monthlyRent는 필수입니다.")
        @Min(value = 0, message = "monthlyRent는 0 이상이어야 합니다.")
        Integer monthlyRent
) {
}
