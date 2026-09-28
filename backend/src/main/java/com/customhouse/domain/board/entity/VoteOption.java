package com.customhouse.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [담당: 미정 - 커뮤니티 게시판] HOUSING 게시글의 투표 항목. voteCount는 VoteOptionRepository의 원자 UPDATE로만 올린다.
 */
@Entity
@Table(name = "vote_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private long voteCount;

    public static VoteOption of(Post post, String label, int sortOrder) {
        VoteOption option = new VoteOption();
        option.post = post;
        option.label = label;
        option.sortOrder = sortOrder;
        return option;
    }
}
