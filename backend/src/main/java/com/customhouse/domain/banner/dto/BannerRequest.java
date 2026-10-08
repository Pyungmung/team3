package com.customhouse.domain.banner.dto;

import com.customhouse.domain.banner.entity.Banner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * [담당: 송귀성] 관리자 배너 등록/수정 요청 (2026-10-08). 링크/이미지 주소·자치구·자리 이름의 세부 검증은 BannerService가 한다.
 */
public record BannerRequest(
        @NotBlank(message = "배너 제목을 입력해주세요.")
        @Size(max = 200, message = "배너 제목은 200자 이하로 입력해주세요.")
        String title,

        @NotNull(message = "카테고리를 선택해주세요.")
        Banner.Category category,

        @NotBlank(message = "배너 이미지를 올려주세요.")
        @Size(max = 500, message = "이미지 주소가 너무 길어요.")
        String imageUrl,

        @NotBlank(message = "클릭하면 이동할 링크를 입력해주세요.")
        @Size(max = 1000, message = "링크가 너무 길어요.")
        String linkUrl,

        @Size(max = 200, message = "이미지 설명은 200자 이하로 입력해주세요.")
        String altText,

        Banner.Segment segment,

        List<String> regions,

        Banner.MoveWithin moveWithin,

        List<String> slots,

        LocalDate startsOn,

        LocalDate endsOn,

        Boolean active,

        Integer sortOrder
) {
}
