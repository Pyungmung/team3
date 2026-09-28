package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 Repository.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {
}
