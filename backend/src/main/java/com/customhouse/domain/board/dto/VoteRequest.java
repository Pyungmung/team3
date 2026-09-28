package com.customhouse.domain.board.dto;

import jakarta.validation.constraints.NotNull;

/**
 * [담당: 미정 - 커뮤니티 게시판] 투표 요청.
 */
public record VoteRequest(
        @NotNull(message = "투표할 항목을 선택해주세요.") Long optionId
) {
}
