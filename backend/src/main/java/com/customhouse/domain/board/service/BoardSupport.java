package com.customhouse.domain.board.service;

import com.customhouse.domain.board.dto.PostDetailResponse;
import com.customhouse.domain.board.entity.Comment;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.entity.VoteOption;
import com.customhouse.domain.user.entity.User;
import com.customhouse.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시판 서비스들이 함께 쓰는 헬퍼.
 * 작성자 닉네임을 한 번에 조회(N+1 방지)하고, 익명 글에서 작성자가 드러나지 않도록 표시명을 결정한다.
 */
@Component
@RequiredArgsConstructor
public class BoardSupport {

    private static final String ANONYMOUS = "익명";
    private static final String ANONYMOUS_AUTHOR = "익명(글쓴이)";
    private static final String UNKNOWN_USER = "알 수 없음";

    private final UserRepository userRepository;

    /** userId → 닉네임 (탈퇴 등으로 없는 id는 결과에 빠진다). */
    public Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), user.getNickname());
        }
        return names;
    }

    /** 글 작성자 표시명. 익명 글이면 닉네임을 조회하지 않아도 되도록 "익명"을 돌려준다. */
    public String postWriterName(Post post, Map<Long, String> names) {
        return post.isAnonymous() ? ANONYMOUS : names.getOrDefault(post.getWriterId(), UNKNOWN_USER);
    }

    /** 댓글 작성자 표시명. 익명 글에서는 글쓴이의 댓글이 닉네임으로 정체가 드러나지 않게 "익명(글쓴이)"로 표시한다. */
    public String commentWriterName(Post post, Long commentWriterId, Map<Long, String> names) {
        if (post.isAnonymous() && post.isWrittenBy(commentWriterId)) {
            return ANONYMOUS_AUTHOR;
        }
        return names.getOrDefault(commentWriterId, UNKNOWN_USER);
    }

    public PostDetailResponse.CommentResponse commentResponse(Comment comment, Post post, Long viewerId,
                                                              Map<Long, String> names,
                                                              List<PostDetailResponse.CommentResponse> replies) {
        return new PostDetailResponse.CommentResponse(
                comment.getId(), comment.getParentId(),
                commentWriterName(post, comment.getWriterId(), names),
                post.isWrittenBy(comment.getWriterId()),
                viewerId != null && viewerId.equals(comment.getWriterId()),
                comment.getContent(), comment.isSelected(), comment.getCreatedAt(), replies);
    }

    public List<PostDetailResponse.VoteOptionResponse> voteOptionResponses(List<VoteOption> options) {
        long total = options.stream().mapToLong(VoteOption::getVoteCount).sum();
        return options.stream()
                .map(o -> new PostDetailResponse.VoteOptionResponse(
                        o.getId(), o.getLabel(), o.getVoteCount(),
                        total == 0 ? 0 : (int) Math.round(o.getVoteCount() * 100.0 / total)))
                .toList();
    }
}
