package com.customhouse.domain.board.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 좋아요. (post_id, user_id) 유니크라 중복 좋아요가 DB 차원에서 막힌다.
 * 게시글/사용자는 FK 없이 id만 저장한다 (게시글 삭제 시 서비스에서 함께 지운다).
 */
@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(name = "uk_like_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static PostLike of(Long postId, Long userId) {
        PostLike like = new PostLike();
        like.postId = postId;
        like.userId = userId;
        return like;
    }
}
