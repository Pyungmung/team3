package com.customhouse.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 수정 요청.
 */
public record CommentUpdateRequest(
        @NotBlank(message = "내용을 입력해주세요.") @Size(max = 2000, message = "댓글은 2,000자 이하로 입력해주세요.") String content
) {
}
