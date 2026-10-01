package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 Repository.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 최상위 댓글을 지울 때 딸린 대댓글 수를 함께 세어 게시글의 commentCount에서 뺀다. */
    long countByParentId(Long parentId);

    void deleteByParentId(Long parentId);
}
