package com.customhouse.domain.board.service;

import com.customhouse.domain.board.dto.CommentCreateRequest;
import com.customhouse.domain.board.dto.PostDetailResponse;
import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Comment;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.repository.CommentRepository;
import com.customhouse.domain.board.repository.PostRepository;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글/대댓글 작성/수정/삭제, SAFETY 답변 채택.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardSupport support;
    private final AdminGuard adminGuard;

    @Transactional
    public PostDetailResponse.CommentResponse create(Long userId, Long postId, CommentCreateRequest req) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (req.parentId() != null) {
            Comment parent = commentRepository.findById(req.parentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
            if (!parent.getPost().getId().equals(postId)) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "다른 게시글의 댓글에는 답글을 달 수 없습니다.");
            }
            if (parent.getParentId() != null) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "답글에는 다시 답글을 달 수 없습니다.");
            }
        }

        Comment saved = commentRepository.save(Comment.of(post, userId, req.parentId(), req.content().trim()));
        postRepository.increaseCommentCount(postId);

        return support.commentResponse(saved, post, userId, support.nicknames(List.of(userId)), List.of());
    }

    /** 답변 채택: SAFETY 글의 최상위 댓글, 글쓴이만, 본인 댓글 제외, 글당 1개. */
    @Transactional
    public void select(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        Post post = comment.getPost();

        if (post.getCategory() != BoardCategory.SAFETY) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "답변 채택은 전세사기·법률 고민 게시판에서만 가능합니다.");
        }
        if (!post.isWrittenBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "글쓴이만 답변을 채택할 수 있습니다.");
        }
        if (comment.getParentId() != null) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "답글은 채택할 수 없습니다.");
        }
        if (comment.getWriterId().equals(userId)) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "본인의 댓글은 채택할 수 없습니다.");
        }

        // solved=false인 경우에만 갱신 → 동시에 두 번 채택해도 하나만 성공
        if (postRepository.markSolvedIfNot(post.getId()) == 0) {
            throw new CustomException(ErrorCode.ALREADY_SELECTED);
        }
        // 위 UPDATE가 영속성 컨텍스트를 비웠으므로 댓글을 다시 읽어 채택 처리한다
        commentRepository.findById(commentId).orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND)).select();
    }

    /** 작성자 본인만 수정할 수 있다. */
    @Transactional
    public void update(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isWrittenBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인이 작성한 댓글만 수정할 수 있습니다.");
        }
        comment.updateContent(content);
    }

    /** 작성자 본인이거나 관리자여야 삭제할 수 있다. 최상위 댓글이면 딸린 대댓글도 함께 지운다. */
    @Transactional
    public void delete(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isWrittenBy(userId)) {
            adminGuard.requireAdmin(userId);
        }

        Long postId = comment.getPost().getId();
        long removed = 1;
        if (comment.getParentId() == null) {
            removed += commentRepository.countByParentId(commentId);
            commentRepository.deleteByParentId(commentId);
        }
        commentRepository.delete(comment);
        for (int i = 0; i < removed; i++) {
            postRepository.decreaseCommentCount(postId);
        }
    }
}
