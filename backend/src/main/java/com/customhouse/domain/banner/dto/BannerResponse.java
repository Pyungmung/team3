package com.customhouse.domain.banner.dto;

import com.customhouse.domain.banner.entity.Banner;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * [담당: 송귀성] 배너 응답 (공개 목록과 관리자 목록 공용, 2026-10-08). regions/slots는 비어 있으면 제한 없음.
 */
public record BannerResponse(
        Long id,
        String title,
        Banner.Category category,
        String imageUrl,
        String linkUrl,
        String altText,
        Banner.Segment segment,
        List<String> regions,
        Banner.MoveWithin moveWithin,
        List<String> slots,
        LocalDate startsOn,
        LocalDate endsOn,
        boolean active,
        int sortOrder
) {
    public static BannerResponse of(Banner b) {
        return new BannerResponse(b.getId(), b.getTitle(), b.getCategory(), b.getImageUrl(), b.getLinkUrl(), b.getAltText(), b.getSegment(),
                split(b.getRegions()), b.getMoveWithin(), split(b.getSlots()), b.getStartsOn(), b.getEndsOn(), b.isActive(), b.getSortOrder());
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
