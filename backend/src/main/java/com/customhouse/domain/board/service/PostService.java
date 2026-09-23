package com.customhouse.domain.board.service;

import com.customhouse.domain.board.dto.PageResponse;
import com.customhouse.domain.board.dto.PostCreateRequest;
import com.customhouse.domain.board.dto.PostDetailResponse;
import com.customhouse.domain.board.dto.PostSummaryResponse;
import com.customhouse.domain.board.dto.PostUpdateRequest;
import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Comment;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.entity.PostMeta;
import com.customhouse.domain.board.entity.PostMetaKey;
import com.customhouse.domain.board.repository.PostLikeRepository;
import com.customhouse.domain.board.repository.PostMetaRepository;
import com.customhouse.domain.board.repository.PostRepository;
import com.customhouse.domain.board.repository.PostScrapRepository;
import com.customhouse.domain.board.repository.VoteRecordRepository;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 작성/수정/삭제/목록/상세/내 스크랩 목록.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_VOTE_OPTIONS = 2;
    private static final int MAX_VOTE_OPTIONS = 5;

    private final PostRepository postRepository;
    private final PostMetaRepository postMetaRepository;
    private final VoteRecordRepository voteRecordRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostScrapRepository postScrapRepository;
    private final BoardSupport support;

    @Transactional
    public Long create(Long userId, PostCreateRequest req) {
        BoardCategory category = req.category();
        if (req.isAnonymous() && category != BoardCategory.SAFETY) {
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "익명 작성은 전세사기·법률 고민 게시판에서만 가능합니다.");
        }
        List<String> options = req.voteOptions() == null ? List.of() : req.voteOptions().stream().map(String::trim).toList();
        if (!options.isEmpty()) {
            if (category != BoardCategory.HOUSING) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "투표는 집 구하기 고민 게시판에서만 만들 수 있습니다.");
            }
            if (options.size() < MIN_VOTE_OPTIONS || options.size() > MAX_VOTE_OPTIONS) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR,
                        "투표 항목은 " + MIN_VOTE_OPTIONS + "~" + MAX_VOTE_OPTIONS + "개여야 합니다.");
            }
            if (new HashSet<>(options).size() != options.size()) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, "투표 항목이 중복되었습니다.");
            }
        }
        validateMetas(category, req.metas());

        Post post = Post.create(category, req.title().trim(), req.content().trim(), userId, req.isAnonymous());
        applyMetas(post, req.metas());
        for (int i = 0; i < options.size(); i++) {
            post.addVoteOption(options.get(i), i);
        }
        return postRepository.save(post).getId();
    }

    @Transactional
    public void update(Long userId, Long postId, PostUpdateRequest req) {
        Post post = getPost(postId);
        requireWriter(post, userId);
        if (req.metas() != null) {
            validateMetas(post.getCategory(), req.metas());
        }
        post.update(req.title().trim(), req.content().trim());
        if (req.metas() != null) {
            post.clearMetas();
            postRepository.flush(); // 기존 행 삭제를 먼저 반영한 뒤 새 행을 넣는다
            applyMetas(post, req.metas());
        }
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = getPost(postId);
        requireWriter(post, userId);
        voteRecordRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        postScrapRepository.deleteByPostId(postId);
        postRepository.delete(post); // 메타/투표 항목/댓글은 cascade로 함께 삭제
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> list(BoardCategory category, int page, int size, String sort) {
        Page<Post> result = postRepository.findByCategory(category, pageable(page, size, sortOf(sort)));
        return toSummaryPage(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> scraps(Long userId, int page, int size) {
        Page<Post> result = postRepository.findScrappedBy(userId, pageable(page, size, sortOf("latest")));
        return toSummaryPage(result);
    }

    /** 상세 조회. 조회수를 먼저 올린 뒤 읽어서 응답의 viewCount에 이번 조회가 반영된다. viewerId는 비로그인이면 null. */
    @Transactional
    public PostDetailResponse detail(Long postId, Long viewerId) {
        if (postRepository.increaseViewCount(postId) == 0) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }
        Post post = getPost(postId);

        Set<Long> userIds = new HashSet<>();
        if (!post.isAnonymous()) {
            userIds.add(post.getWriterId());
        }
        for (Comment c : post.getComments()) {
            userIds.add(c.getWriterId());
        }
        Map<Long, String> names = support.nicknames(userIds);

        // 댓글 트리: 최상위 댓글 아래에 대댓글(1단계)
        Map<Long, List<PostDetailResponse.CommentResponse>> repliesByParent = new HashMap<>();
        for (Comment c : post.getComments()) {
            if (c.getParentId() != null) {
                repliesByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                        .add(support.commentResponse(c, post, viewerId, names, List.of()));
            }
        }
        List<PostDetailResponse.CommentResponse> comments = post.getComments().stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> support.commentResponse(c, post, viewerId, names,
                        repliesByParent.getOrDefault(c.getId(), List.of())))
                .toList();

        Long myVote = null;
        boolean liked = false;
        boolean scrapped = false;
        if (viewerId != null) {
            myVote = voteRecordRepository.findByPostIdAndUserId(postId, viewerId).map(v -> v.getOptionId()).orElse(null);
            liked = postLikeRepository.existsByPostIdAndUserId(postId, viewerId);
            scrapped = postScrapRepository.existsByPostIdAndUserId(postId, viewerId);
        }

        return new PostDetailResponse(
                post.getId(), post.getCategory(), post.getTitle(), post.getContent(),
                support.postWriterName(post, names), post.isAnonymous(),
                viewerId != null && post.isWrittenBy(viewerId),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(), post.isSolved(),
                liked, scrapped, post.getCreatedAt(),
                post.getMetas().stream().map(m -> new PostDetailResponse.MetaResponse(m.getMetaKey(), m.getMetaValue())).toList(),
                support.voteOptionResponses(post.getVoteOptions()),
                myVote, comments);
    }

    private PageResponse<PostSummaryResponse> toSummaryPage(Page<Post> result) {
        List<Post> posts = result.getContent();
        Set<Long> writerIds = new HashSet<>();
        List<Long> postIds = new ArrayList<>();
        for (Post p : posts) {
            postIds.add(p.getId());
            if (!p.isAnonymous()) {
                writerIds.add(p.getWriterId());
            }
        }
        Map<Long, String> names = support.nicknames(writerIds);

        // 썸네일: 글마다 첫 번째 IMAGE_URL (쿼리가 글/순서대로 정렬되어 있어 처음 만난 값을 쓴다)
        Map<Long, String> thumbnails = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (PostMeta m : postMetaRepository.findByPostIdsAndKey(postIds, PostMetaKey.IMAGE_URL)) {
                thumbnails.putIfAbsent(m.getPost().getId(), m.getMetaValue());
            }
        }
        List<PostSummaryResponse> content = posts.stream()
                .map(p -> PostSummaryResponse.of(p, support.postWriterName(p, names), thumbnails.get(p.getId())))
                .toList();
        return PageResponse.of(result, content);
    }

    private Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private Sort sortOf(String sort) {
        String key = sort == null ? "latest" : sort.toLowerCase();
        return switch (key) {
            case "latest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "popular" -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("viewCount"), Sort.Order.desc("id"));
            case "views" -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("id"));
            default -> throw new CustomException(ErrorCode.VALIDATION_ERROR, "sort는 latest, popular, views 중 하나여야 합니다.");
        };
    }

    private void validateMetas(BoardCategory category, List<PostCreateRequest.MetaItem> metas) {
        if (metas == null) {
            return;
        }
        Set<PostMetaKey> seen = EnumSet.noneOf(PostMetaKey.class);
        for (PostCreateRequest.MetaItem item : metas) {
            PostMetaKey key = item.key();
            if (!key.isAllowedIn(category)) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, key + " 항목은 이 게시판에서 사용할 수 없습니다.");
            }
            if (item.value().trim().length() > key.getMaxLength()) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR,
                        key + " 값은 " + key.getMaxLength() + "자 이하여야 합니다.");
            }
            if (!key.isRepeatable() && !seen.add(key)) {
                throw new CustomException(ErrorCode.VALIDATION_ERROR, key + " 항목은 한 번만 입력할 수 있습니다.");
            }
        }
    }

    /** 같은 키가 여러 개면 입력 순서를 sortOrder로 남긴다. */
    private void applyMetas(Post post, List<PostCreateRequest.MetaItem> metas) {
        if (metas == null) {
            return;
        }
        Map<PostMetaKey, Integer> counters = new LinkedHashMap<>();
        for (PostCreateRequest.MetaItem item : metas) {
            int order = counters.merge(item.key(), 1, Integer::sum) - 1;
            post.addMeta(item.key(), item.value().trim(), order);
        }
    }

    private Post getPost(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    private void requireWriter(Post post, Long userId) {
        if (!post.isWrittenBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "본인이 작성한 글만 수정/삭제할 수 있습니다.");
        }
    }
}
