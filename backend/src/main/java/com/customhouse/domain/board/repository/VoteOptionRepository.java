package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.VoteOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 투표 항목 Repository. 득표수는 원자 UPDATE로 올린다.
 */
public interface VoteOptionRepository extends JpaRepository<VoteOption, Long> {

    List<VoteOption> findByPost_IdOrderBySortOrderAscIdAsc(Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VoteOption o set o.voteCount = o.voteCount + 1 where o.id = :id")
    int increaseVoteCount(@Param("id") Long id);
}
