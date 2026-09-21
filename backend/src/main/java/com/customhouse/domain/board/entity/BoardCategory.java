package com.customhouse.domain.board.entity;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시판 카테고리.
 * 테이블이 아니라 enum으로 두고 posts.category에 문자열로 저장한다 (카테고리는 코드로 고정된 4종).
 */
public enum BoardCategory {
    HOUSING("집 구하기 고민"),     // 매물 요약 첨부, 찬반/선택 투표
    INTERIOR("인테리어 고민"),     // 사진 슬라이드, 주거 형태/평수 태그, 이미지 핀 태그
    SAFETY("전세사기·법률 고민"),   // 질문/답변 채택, 익명 작성
    COMMUNITY("자취 꿀팁·수다");   // 팁 스크랩, 인기글

    private final String label;

    BoardCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
