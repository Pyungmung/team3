package com.customhouse.domain.board.dto;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Post;

import java.time.LocalDateTime;

/**
 * [담당: 미정 - 커뮤니티 게시판] 목록 항목. 익명 글은 작성자 정보를 "익명"으로만 내려준다.
 */
public record PostSummaryResponse(
        Long id,
        BoardCategory category,
        String title,
        String writerName,
        boolean anonymous,
        long viewCount,
        long likeCount,
        long commentCount,
        boolean solved,
        String thumbnailUrl,
        LocalDateTime createdAt
) {

    public static PostSummaryResponse of(Post post, String writerName, String thumbnailUrl) {
        return new PostSummaryResponse(
                post.getId(), post.getCategory(), post.getTitle(),
                post.isAnonymous() ? "익명" : writerName, post.isAnonymous(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                post.isSolved(), thumbnailUrl, post.getCreatedAt());
    }
}
