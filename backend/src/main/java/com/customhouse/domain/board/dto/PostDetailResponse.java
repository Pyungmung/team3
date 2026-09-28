package com.customhouse.domain.board.dto;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.PostMetaKey;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 상세 응답.
 * 익명 글이면 writerName은 "익명"이고 작성자 id/닉네임은 어디에도 담지 않는다 (본인 여부는 mine 플래그로만 알려준다).
 */
public record PostDetailResponse(
        Long id,
        BoardCategory category,
        String title,
        String content,
        String writerName,
        boolean anonymous,
        boolean mine,
        long viewCount,
        long likeCount,
        long commentCount,
        boolean solved,
        boolean liked,
        boolean scrapped,
        LocalDateTime createdAt,
        List<MetaResponse> metas,
        List<VoteOptionResponse> voteOptions,
        Long myVoteOptionId,
        List<CommentResponse> comments
) {

    public record MetaResponse(PostMetaKey key, String value) {
    }

    public record VoteOptionResponse(Long id, String label, long voteCount, int percent) {
    }

    /** replies는 최상위 댓글에만 채워진다 (대댓글은 1단계까지). */
    public record CommentResponse(
            Long id,
            Long parentId,
            String writerName,
            boolean postAuthor,
            boolean mine,
            String content,
            boolean selected,
            LocalDateTime createdAt,
            List<CommentResponse> replies
    ) {
    }
}
