package com.customhouse.domain.board.repository;

import com.customhouse.domain.board.entity.PostMeta;
import com.customhouse.domain.board.entity.PostMetaKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 부가정보 Repository. 목록의 썸네일을 한 번에 가져오는 용도.
 */
public interface PostMetaRepository extends JpaRepository<PostMeta, Long> {

    @Query("select m from PostMeta m where m.post.id in :postIds and m.metaKey = :key order by m.post.id, m.sortOrder, m.id")
    List<PostMeta> findByPostIdsAndKey(@Param("postIds") Collection<Long> postIds, @Param("key") PostMetaKey key);
}
