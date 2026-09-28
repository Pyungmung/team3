package com.customhouse.domain.board.entity;

import com.customhouse.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 엔티티.
 * 작성자는 다른 도메인처럼 users를 @ManyToOne으로 걸지 않고 writerId(Long)로만 참조한다 (FK 없음).
 * 익명 글도 writerId는 저장하지만(본인 확인/관리용) API 응답에는 절대 내려보내지 않는다.
 * 조회수/좋아요/댓글수 카운터는 동시 요청에도 안 어긋나도록 PostRepository의 원자 UPDATE 쿼리로만 바꾼다.
 */
@Entity
@Table(name = "posts", indexes = @Index(name = "idx_posts_category_created", columnList = "category, createdAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoardCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Long writerId;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long commentCount;

    /** SAFETY 게시판에서 답변이 채택됐는지 */
    @Column(nullable = false)
    private boolean solved;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<PostMeta> metas = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<VoteOption> voteOptions = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Comment> comments = new ArrayList<>();

    public static Post create(BoardCategory category, String title, String content, Long writerId, boolean anonymous) {
        Post post = new Post();
        post.category = category;
        post.title = title;
        post.content = content;
        post.writerId = writerId;
        post.anonymous = anonymous;
        return post;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return writerId.equals(userId);
    }

    public void addMeta(PostMetaKey key, String value, int sortOrder) {
        metas.add(PostMeta.of(this, key, value, sortOrder));
    }

    /** 부가정보를 통째로 교체한다 (orphanRemoval로 기존 행은 삭제됨). */
    public void clearMetas() {
        metas.clear();
    }

    public void addVoteOption(String label, int sortOrder) {
        voteOptions.add(VoteOption.of(this, label, sortOrder));
    }
}
