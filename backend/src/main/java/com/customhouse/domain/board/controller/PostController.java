package com.customhouse.domain.board.controller;

import com.customhouse.domain.board.dto.CommentCreateRequest;
import com.customhouse.domain.board.dto.PageResponse;
import com.customhouse.domain.board.dto.PostCreateRequest;
import com.customhouse.domain.board.dto.PostDetailResponse;
import com.customhouse.domain.board.dto.PostSummaryResponse;
import com.customhouse.domain.board.dto.PostUpdateRequest;
import com.customhouse.domain.board.dto.ReactionResponse;
import com.customhouse.domain.board.dto.VoteRequest;
import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.service.CommentService;
import com.customhouse.domain.board.service.PostReactionService;
import com.customhouse.domain.board.service.PostService;
import com.customhouse.global.common.ApiResponse;
import com.customhouse.global.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 API.
 * 조회(GET)는 공개(로그인 상태면 내 글/좋아요/스크랩/투표 여부가 함께 내려감), 쓰기는 로그인 필요 (SecurityConfig 참고).
 * 주의: "/scraps"는 "/{id}"보다 먼저 매칭되도록 리터럴 경로로 둔다.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final PostReactionService reactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostSummaryResponse>>> list(
            @RequestParam(required = false) BoardCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        return ResponseEntity.ok(ApiResponse.ok(postService.list(category, page, size, sort)));
    }

    @GetMapping("/scraps")
    public ResponseEntity<ApiResponse<PageResponse<PostSummaryResponse>>> myScraps(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(postService.scraps(principal.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> detail(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        Long viewerId = principal == null ? null : principal.id();
        return ResponseEntity.ok(ApiResponse.ok(postService.detail(id, viewerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody PostCreateRequest request) {
        Long id = postService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("게시글이 등록되었습니다.", Map.of("id", id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request) {
        postService.update(principal.id(), id, request);
        return ResponseEntity.ok(ApiResponse.ok("게시글이 수정되었습니다.", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        postService.delete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("게시글이 삭제되었습니다.", null));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<PostDetailResponse.CommentResponse>> addComment(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("댓글이 등록되었습니다.", commentService.create(principal.id(), id, request)));
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<ApiResponse<ReactionResponse.Vote>> vote(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id,
            @Valid @RequestBody VoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reactionService.vote(principal.id(), id, request.optionId())));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<ReactionResponse.Like>> like(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(reactionService.toggleLike(principal.id(), id)));
    }

    @PostMapping("/{id}/scrap")
    public ResponseEntity<ApiResponse<ReactionResponse.Scrap>> scrap(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(reactionService.toggleScrap(principal.id(), id)));
    }
}
