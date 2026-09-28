package com.customhouse.domain.board.entity;

import com.customhouse.global.common.BaseTimeEntity;
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

import java.time.LocalDateTime;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글/답변/대댓글.
 * parentId가 null이면 최상위 댓글(SAFETY에서는 "답변"), 값이 있으면 그 댓글에 대한 대댓글이다 (1단계까지만 허용).
 * 채택(isSelected)은 SAFETY 게시글의 최상위 댓글에만 붙는다.
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private Long writerId;

    private Long parentId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    private LocalDateTime selectedAt;

    public static Comment of(Post post, Long writerId, Long parentId, String content) {
        Comment comment = new Comment();
        comment.post = post;
        comment.writerId = writerId;
        comment.parentId = parentId;
        comment.content = content;
        return comment;
    }

    public void select() {
        this.selected = true;
        this.selectedAt = LocalDateTime.now();
    }
}
