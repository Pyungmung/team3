package com.customhouse.domain.appsetting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * [담당: 송귀성] 기타 설정 저장 요청 (관리자 수정 > 기타 설정). 추천 개수 상한은 월세·전세 각각 10~1000건.
 */
public record AppSettingRequest(
        @NotNull(message = "추천 개수 상한을 입력해주세요.")
        @Min(value = 10, message = "추천 개수 상한은 10 이상이어야 합니다.")
        @Max(value = 1000, message = "추천 개수 상한은 1000 이하로 입력해주세요.") Integer recommendationLimit
) {
}
