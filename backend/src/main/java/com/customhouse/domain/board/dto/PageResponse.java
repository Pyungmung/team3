package com.customhouse.domain.board.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 목록 응답용 페이지 래퍼 (Spring Page를 그대로 노출하지 않고 필요한 값만 내려준다).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
