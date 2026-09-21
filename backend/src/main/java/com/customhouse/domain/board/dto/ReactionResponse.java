package com.customhouse.domain.board.dto;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 투표/좋아요/스크랩 요청의 응답 모음.
 */
public final class ReactionResponse {

    private ReactionResponse() {
    }

    public record Vote(Long myOptionId, List<PostDetailResponse.VoteOptionResponse> voteOptions) {
    }

    public record Like(boolean liked, long likeCount) {
    }

    public record Scrap(boolean scrapped) {
    }
}
