package com.customhouse.domain.board.controller;

import com.customhouse.domain.board.service.CommentService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 API (답변 채택). 로그인 필요.
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
}
