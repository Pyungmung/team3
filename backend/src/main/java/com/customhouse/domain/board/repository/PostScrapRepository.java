package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.PostScrap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 미정 - 커뮤니티 게시판] 스크랩 Repository.
 */
public interface PostScrapRepository extends JpaRepository<PostScrap, Long> {

    Optional<PostScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);
}
