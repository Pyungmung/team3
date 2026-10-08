package com.customhouse.domain.appsetting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 송귀성] 기타 설정 저장 요청 (관리자 수정 > 기타 설정). 추천 개수 상한은 월세·전세 각각 10~1000건.
 * 광고하기 가격(100~1,000,000원)/노출 기간(1~365일)은 선택 - 안 보내면(null) 지금 값을 유지한다.
 */
public record AppSettingRequest(
        @NotNull(message = "추천 개수 상한을 입력해주세요.")
        @Min(value = 10, message = "추천 개수 상한은 10 이상이어야 합니다.")
        @Max(value = 1000, message = "추천 개수 상한은 1000 이하로 입력해주세요.") Integer recommendationLimit,
        @Min(value = 100, message = "광고 가격은 100원 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "광고 가격은 1,000,000원 이하로 입력해주세요.") Integer adPriceWon,
        @Min(value = 1, message = "광고 노출 기간은 1일 이상이어야 합니다.")
        @Max(value = 365, message = "광고 노출 기간은 365일 이하로 입력해주세요.") Integer adPeriodDays
) {

    /** 추천 개수 상한만 바꾸는 호출(광고 설정은 그대로). */
    public AppSettingRequest(Integer recommendationLimit) {
        this(recommendationLimit, null, null);
    }
}
