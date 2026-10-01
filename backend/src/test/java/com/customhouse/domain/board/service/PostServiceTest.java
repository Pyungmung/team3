package com.customhouse.domain.board.service;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.repository.PostLikeRepository;
import com.customhouse.domain.board.repository.PostMetaRepository;
import com.customhouse.domain.board.repository.PostRepository;
import com.customhouse.domain.board.repository.PostScrapRepository;
import com.customhouse.domain.board.repository.VoteRecordRepository;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [담당: 미정 - 커뮤니티 게시판] 게시글 삭제 권한 테스트: 작성자 본인이거나 관리자만 삭제할 수 있고,
 * 수정은 관리자여도 작성자가 아니면 여전히 막히는지 확인한다(요청 사항: 관리자는 삭제만 가능).
 */
class PostServiceTest {

    private static final Long WRITER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private PostRepository postRepository;
    private AdminGuard adminGuard;
    private PostService service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        PostMetaRepository postMetaRepository = mock(PostMetaRepository.class);
        VoteRecordRepository voteRecordRepository = mock(VoteRecordRepository.class);
        PostLikeRepository postLikeRepository = mock(PostLikeRepository.class);
        PostScrapRepository postScrapRepository = mock(PostScrapRepository.class);
        BoardSupport support = mock(BoardSupport.class);
        adminGuard = mock(AdminGuard.class);
        service = new PostService(postRepository, postMetaRepository, voteRecordRepository,
                postLikeRepository, postScrapRepository, support, adminGuard);
    }

    private Post post() {
        Post post = Post.create(BoardCategory.HOUSING, "제목", "내용", WRITER_ID, false);
        ReflectionTestUtils.setField(post, "id", 10L);
        return post;
    }

    @Test
    void 작성자_본인이면_삭제할_수_있다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));

        assertThatCode(() -> service.delete(WRITER_ID, 10L)).doesNotThrowAnyException();
    }

    @Test
    void 작성자가_아니어도_관리자면_삭제할_수_있다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));
        doNothing().when(adminGuard).requireAdmin(OTHER_ID);

        assertThatCode(() -> service.delete(OTHER_ID, 10L)).doesNotThrowAnyException();
    }

    @Test
    void 작성자도_관리자도_아니면_삭제시_FORBIDDEN() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(adminGuard).requireAdmin(OTHER_ID);

        assertThatThrownBy(() -> service.delete(OTHER_ID, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 관리자여도_작성자가_아니면_수정은_못한다() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post()));
        var req = new com.customhouse.domain.board.dto.PostUpdateRequest("새 제목", "새 내용", null);

        assertThatThrownBy(() -> service.update(OTHER_ID, 10L, req))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        // update()는 requireWriter만 써서 adminGuard를 아예 호출하지 않는다
        org.mockito.Mockito.verifyNoInteractions(adminGuard);
    }
}
