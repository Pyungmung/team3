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
 * [담당: 미정 - 커뮤니티 게시판] 투표 내역. (post_id, user_id) 유니크라 한 사용자는 게시글당 한 번만 투표할 수 있다.
 * 게시글/사용자는 FK 없이 id만 저장한다 (다른 도메인과 같은 방식, 게시글 삭제 시 서비스에서 함께 지운다).
 */
@Entity
@Table(name = "vote_records", uniqueConstraints = @UniqueConstraint(name = "uk_vote_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long optionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static VoteRecord of(Long postId, Long optionId, Long userId) {
        VoteRecord record = new VoteRecord();
        record.postId = postId;
        record.optionId = optionId;
        record.userId = userId;
        return record;
    }
}
