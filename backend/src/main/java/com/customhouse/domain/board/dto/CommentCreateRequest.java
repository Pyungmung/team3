package com.customhouse.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글/답변/대댓글 작성 요청. parentId가 있으면 그 댓글에 대한 대댓글.
 */
public record CommentCreateRequest(
        @NotBlank(message = "내용을 입력해주세요.") @Size(max = 2000, message = "댓글은 2,000자 이하로 입력해주세요.") String content,
        Long parentId
) {
}
