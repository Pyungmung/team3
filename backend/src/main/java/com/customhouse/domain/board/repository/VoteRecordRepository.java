package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.VoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * [담당: 미정 - 커뮤니티 게시판] 투표 내역 Repository.
 */
public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {

    Optional<VoteRecord> findByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);
}
