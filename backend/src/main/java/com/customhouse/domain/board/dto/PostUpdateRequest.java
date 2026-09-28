package com.customhouse.domain.board.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 수정 요청. 카테고리/익명 여부/투표 항목은 바꿀 수 없다.
 * metas가 null이면 기존 부가정보를 그대로 두고, 빈 배열이면 전부 지운다.
 */
public record PostUpdateRequest(
        @NotBlank(message = "제목을 입력해주세요.") @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.") String title,
        @NotBlank(message = "내용을 입력해주세요.") @Size(max = 10000, message = "내용은 10,000자 이하로 입력해주세요.") String content,
        @Valid List<PostCreateRequest.MetaItem> metas
) {
}
