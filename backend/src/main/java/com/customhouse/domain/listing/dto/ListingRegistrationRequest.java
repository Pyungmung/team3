package com.customhouse.domain.listing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * [담당: 송귀성] 회원 매물 등록 요청. 주소는 검색어(도로명/지번주소)만 받고, 자치구·법정동·좌표·우편번호는
 * AI 엔진(customhouse-ai, listing_builder.resolve_keyword)이 행안부/카카오 API로 직접 채운다 -
 * 브라우저가 자치구를 조작해서 엉뚱한 CSV에 끼워 넣을 수 없다.
 */
public record ListingRegistrationRequest(
        @NotBlank(message = "주소를 입력해주세요.") @Size(max = 300, message = "주소가 너무 깁니다.") String addressKeyword,
        @NotBlank(message = "매물유형을 선택해주세요.")
        @Pattern(regexp = "아파트|오피스텔|연립다세대|단독다가구", message = "매물유형이 올바르지 않습니다.") String propertyType,
        @NotBlank(message = "거래유형을 선택해주세요.")
        @Pattern(regexp = "전세|월세", message = "거래유형이 올바르지 않습니다.") String leaseType,
        @NotNull(message = "보증금을 입력해주세요.") @PositiveOrZero(message = "보증금은 0 이상이어야 합니다.") @Max(value = 1_000_000, message = "보증금이 너무 큽니다.") Integer deposit,
        @NotNull(message = "월세를 입력해주세요(전세는 0).") @PositiveOrZero(message = "월세는 0 이상이어야 합니다.") @Max(value = 5_000, message = "월세가 너무 큽니다.") Integer monthlyRent,
        @NotNull(message = "전용면적을 입력해주세요.") @Min(value = 1, message = "전용면적이 올바르지 않습니다.") @Max(value = 1000, message = "전용면적이 너무 큽니다.") Double exclusiveArea,
        @Size(max = 100) String buildingName,
        @Size(max = 50) String unitLabel,
        @Size(max = 10) String floor,
        @Min(value = 0, message = "방 수는 0 이상이어야 합니다.") @Max(value = 20, message = "방 수가 너무 많습니다.") Integer rooms,
        @Min(value = 0, message = "욕실 수는 0 이상이어야 합니다.") @Max(value = 20, message = "욕실 수가 너무 많습니다.") Integer bathrooms,
        @Min(value = 1900, message = "건축년도가 올바르지 않습니다.") @Max(value = 2035, message = "건축년도가 올바르지 않습니다.") Integer builtYear,
        @Min(value = 0, message = "관리비는 0 이상이어야 합니다.") @Max(value = 1000, message = "관리비가 너무 큽니다.") Integer maintenanceFee,
        @Size(max = 100) String maintenanceFeeItems,
        @Size(max = 50) String parking,
        Boolean elevator,
        @Size(max = 20) @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "이사가능일은 YYYY-MM-DD 형식이어야 합니다.") String moveInDate,
        @Size(max = 1000) String description,
        Boolean jeonseLoanAvailable,
        @Size(max = 300) @Pattern(regexp = "^$|^(https?://[^/\\s]+)?/uploads/listings/[^/\\s]+$", message = "사진 주소가 올바르지 않습니다.") String photoUrl,
        // 공인중개사 정보 - 모두 선택 입력 (더미 매물 CSV의 공인중개사_* 6컬럼)
        @Size(max = 100) String brokerName,
        @Size(max = 50) String brokerRepresentative,
        @Size(max = 50) String brokerRegNo,
        @Size(max = 30) String brokerPhone,
        @Size(max = 200) String brokerAddress,
        @Size(max = 500) String brokerComment
) {
}
