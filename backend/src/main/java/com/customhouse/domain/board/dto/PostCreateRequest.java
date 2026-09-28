package com.customhouse.domain.board.dto;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.PostMetaKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 작성 요청.
 * 카테고리별 규칙(익명은 SAFETY만, 투표 항목은 HOUSING만 2~5개, 메타는 허용 키만)은 PostService에서 검증한다.
 */
public record PostCreateRequest(
        @NotNull(message = "카테고리를 선택해주세요.") BoardCategory category,
        @NotBlank(message = "제목을 입력해주세요.") @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.") String title,
        @NotBlank(message = "내용을 입력해주세요.") @Size(max = 10000, message = "내용은 10,000자 이하로 입력해주세요.") String content,
        Boolean anonymous, // 생략하면 false (Jackson 3는 원시 boolean 누락을 거부하므로 래퍼 타입 사용)
        @Valid List<MetaItem> metas,
        List<@NotBlank(message = "투표 항목은 비워둘 수 없습니다.") @Size(max = 50, message = "투표 항목은 50자 이하로 입력해주세요.") String> voteOptions
) {

    public boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous);
    }

    public record MetaItem(
            @NotNull(message = "부가정보 키가 필요합니다.") PostMetaKey key,
            @NotBlank(message = "부가정보 값이 필요합니다.") String value
    ) {
    }
}
