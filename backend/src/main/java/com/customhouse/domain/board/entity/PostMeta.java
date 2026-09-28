package com.customhouse.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * [담당: 미정 - 커뮤니티 게시판] 게시글 부가정보(키/값). 매물 요약, 주거 형태/평수 태그, 사진 URL, 이미지 핀 등을
 * 카테고리마다 컬럼을 늘리지 않고 저장하기 위한 확장용 구조 (허용 키는 PostMetaKey 참고).
 */
@Entity
@Table(name = "post_metas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PostMetaKey metaKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metaValue;

    /** 같은 키가 여러 개일 때(사진 슬라이드) 순서 유지용 */
    @Column(nullable = false)
    private int sortOrder;

    public static PostMeta of(Post post, PostMetaKey key, String value, int sortOrder) {
        PostMeta meta = new PostMeta();
        meta.post = post;
        meta.metaKey = key;
        meta.metaValue = value;
        meta.sortOrder = sortOrder;
        return meta;
    }
}
