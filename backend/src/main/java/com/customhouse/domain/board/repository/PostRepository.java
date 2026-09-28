package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 Repository.
 * 카운터는 엔티티 setter가 아니라 원자 UPDATE 쿼리로만 바꾼다 (동시 요청에도 값이 안 어긋나도록).
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /** category가 null이면 전체. 정렬은 Pageable의 Sort로 처리한다. */
    @Query("select p from Post p where (:category is null or p.category = :category)")
    Page<Post> findByCategory(@Param("category") BoardCategory category, Pageable pageable);

    @Query("select p from Post p where p.id in (select s.postId from PostScrap s where s.userId = :userId)")
    Page<Post> findScrappedBy(@Param("userId") Long userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int increaseViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    int addLikeCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
    int increaseCommentCount(@Param("id") Long id);

    /** 아직 채택 전인 경우에만 solved=true로 바꾼다. 반환값 0이면 이미 채택됨(동시 채택 방어). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.solved = true where p.id = :id and p.solved = false")
    int markSolvedIfNot(@Param("id") Long id);
}
