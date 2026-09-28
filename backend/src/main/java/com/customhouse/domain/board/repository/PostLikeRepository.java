package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 미정 - 커뮤니티 게시판] 좋아요 Repository.
 */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);
}
