package com.customhouse.domain.board.controller;

import com.customhouse.domain.board.dto.CommentUpdateRequest;
import com.customhouse.domain.board.service.CommentService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 API (답변 채택/수정/삭제). 로그인 필요.
 * 수정은 작성자 본인만, 삭제는 작성자 본인 또는 관리자가 가능하다.
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/{commentId}/select")
    public ResponseEntity<ApiResponse<Void>> select(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long commentId) {
        commentService.select(principal.id(), commentId);
        return ResponseEntity.ok(ApiResponse.ok("답변이 채택되었습니다.", null));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        commentService.update(principal.id(), commentId, request.content().trim());
        return ResponseEntity.ok(ApiResponse.ok("댓글이 수정되었습니다.", null));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long commentId) {
        commentService.delete(principal.id(), commentId);
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다.", null));
    }
}
