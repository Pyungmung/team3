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
 * [담당: 미정 - 커뮤니티 게시판] 게시글 스크랩(내 스크랩 목록용). (post_id, user_id) 유니크.
 * 게시글/사용자는 FK 없이 id만 저장한다 (게시글 삭제 시 서비스에서 함께 지운다).
 */
@Entity
@Table(name = "post_scraps", uniqueConstraints = @UniqueConstraint(name = "uk_scrap_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostScrap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static PostScrap of(Long postId, Long userId) {
        PostScrap scrap = new PostScrap();
        scrap.postId = postId;
        scrap.userId = userId;
        return scrap;
    }
}
