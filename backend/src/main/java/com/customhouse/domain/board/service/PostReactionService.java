package com.customhouse.domain.board.service;

import com.customhouse.domain.board.dto.ReactionResponse;
import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.entity.PostLike;
import com.customhouse.domain.board.entity.PostScrap;
import com.customhouse.domain.board.entity.VoteRecord;
import com.customhouse.domain.board.repository.PostLikeRepository;
import com.customhouse.domain.board.repository.PostRepository;
import com.customhouse.domain.board.repository.PostScrapRepository;
import com.customhouse.domain.board.repository.VoteOptionRepository;
import com.customhouse.domain.board.repository.VoteRecordRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [담당: 미정 - 커뮤니티 게시판] 투표 / 좋아요 토글 / 스크랩 토글.
 * 중복 방지는 (post_id, user_id) 유니크 제약이 최종 방어선이고, 여기서는 먼저 조회해서 친절한 응답을 준다.
 */
@Service
@RequiredArgsConstructor
public class PostReactionService {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostScrapRepository postScrapRepository;
    private final BoardSupport support;

    @Transactional
    public ReactionResponse.Vote vote(Long userId, Long postId, Long optionId) {
        Post post = getPost(postId);
        if (post.getCategory() != BoardCategory.HOUSING || post.getVoteOptions().isEmpty()) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "투표가 없는 게시글입니다.");
        }
        if (post.getVoteOptions().stream().noneMatch(o -> o.getId().equals(optionId))) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "이 게시글의 투표 항목이 아닙니다.");
        }
        if (voteRecordRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_VOTED);
        }
        try {
            voteRecordRepository.saveAndFlush(VoteRecord.of(postId, optionId, userId));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.ALREADY_VOTED);
        }
        voteOptionRepository.increaseVoteCount(optionId);

        return new ReactionResponse.Vote(optionId,
                support.voteOptionResponses(voteOptionRepository.findByPost_IdOrderBySortOrderAscIdAsc(postId)));
    }

    @Transactional
    public ReactionResponse.Like toggleLike(Long userId, Long postId) {
        getPost(postId);
        boolean liked;
        var existing = postLikeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            postRepository.addLikeCount(postId, -1);
            liked = false;
        } else {
            try {
                postLikeRepository.saveAndFlush(PostLike.of(postId, userId));
            } catch (DataIntegrityViolationException e) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "이미 처리된 요청입니다.");
            }
            postRepository.addLikeCount(postId, 1);
            liked = true;
        }
        return new ReactionResponse.Like(liked, getPost(postId).getLikeCount());
    }

    @Transactional
    public ReactionResponse.Scrap toggleScrap(Long userId, Long postId) {
        getPost(postId);
        var existing = postScrapRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            postScrapRepository.delete(existing.get());
            return new ReactionResponse.Scrap(false);
        }
        try {
            postScrapRepository.saveAndFlush(PostScrap.of(postId, userId));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "이미 처리된 요청입니다.");
        }
        return new ReactionResponse.Scrap(true);
    }

    private Post getPost(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }
}
