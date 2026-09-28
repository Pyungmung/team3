package com.customhouse.domain.board.entity;

import java.util.Set;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 부가정보(PostMeta)에 저장할 수 있는 키 목록.
 * 임의의 키가 DB에 쌓이는 것을 막기 위해 카테고리별로 허용되는 키와 값 길이를 여기서 관리한다.
 * 새 부가정보가 필요하면 이 enum에 키만 추가하면 된다 (테이블 변경 없음).
 */
public enum PostMetaKey {

    // HOUSING - 첨부하는 매물 요약 정보
    LISTING_ADDRESS(Set.of(BoardCategory.HOUSING), 200, false),
    LISTING_DEPOSIT(Set.of(BoardCategory.HOUSING), 30, false),
    LISTING_MONTHLY_RENT(Set.of(BoardCategory.HOUSING), 30, false),
    LISTING_AREA(Set.of(BoardCategory.HOUSING), 30, false),
    LISTING_TYPE(Set.of(BoardCategory.HOUSING), 50, false),
    LISTING_URL(Set.of(BoardCategory.HOUSING), 500, false),

    // INTERIOR - 주거 형태/평수 태그, 사진 슬라이드, 이미지 핀 태그(JSON 문자열)
    HOUSING_TYPE(Set.of(BoardCategory.INTERIOR), 50, false),
    AREA_PYEONG(Set.of(BoardCategory.INTERIOR), 20, false),
    IMAGE_URL(Set.of(BoardCategory.INTERIOR), 500, true),
    IMAGE_PIN(Set.of(BoardCategory.INTERIOR), 4000, true);

    private final Set<BoardCategory> allowedCategories;
    private final int maxLength;
    private final boolean repeatable;

    PostMetaKey(Set<BoardCategory> allowedCategories, int maxLength, boolean repeatable) {
        this.allowedCategories = allowedCategories;
        this.maxLength = maxLength;
        this.repeatable = repeatable;
    }

    public boolean isAllowedIn(BoardCategory category) {
        return allowedCategories.contains(category);
    }

    public int getMaxLength() {
        return maxLength;
    }

    /** 같은 키를 여러 번 넣을 수 있는지 (사진 슬라이드처럼 여러 장인 경우). */
    public boolean isRepeatable() {
        return repeatable;
    }
}
