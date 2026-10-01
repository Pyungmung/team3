package com.customhouse.domain.board.service;

import com.customhouse.domain.board.entity.BoardCategory;
import com.customhouse.domain.board.entity.Comment;
import com.customhouse.domain.board.entity.Post;
import com.customhouse.domain.board.repository.CommentRepository;
import com.customhouse.domain.board.repository.PostRepository;
import com.customhouse.domain.user.service.AdminGuard;
import com.customhouse.global.error.CustomException;
import com.customhouse.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [담당: 미정 - 커뮤니티 게시판] 댓글 수정/삭제 권한 테스트: 수정은 작성자 본인만, 삭제는 작성자 본인
 * 또는 관리자만 가능하고, 최상위 댓글을 지우면 딸린 대댓글도 함께 지워지는지 확인한다.
 */
class CommentServiceTest {

    private static final Long WRITER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private CommentRepository commentRepository;
    private PostRepository postRepository;
    private AdminGuard adminGuard;
    private CommentService service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        BoardSupport support = mock(BoardSupport.class);
        adminGuard = mock(AdminGuard.class);
        service = new CommentService(postRepository, commentRepository, support, adminGuard);
    }

    private Post post() {
        Post post = Post.create(BoardCategory.HOUSING, "제목", "내용", WRITER_ID, false);
        ReflectionTestUtils.setField(post, "id", 100L);
        return post;
    }

    private Comment comment(Long parentId) {
        Comment comment = Comment.of(post(), WRITER_ID, parentId, "댓글");
        ReflectionTestUtils.setField(comment, "id", 5L);
        return comment;
    }

    @Test
    void 작성자_본인이면_댓글을_수정할_수_있다() {
        Comment comment = comment(null);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));

        service.update(WRITER_ID, 5L, "수정된 내용");

        assertThat(comment.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void 작성자가_아니면_수정시_FORBIDDEN() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(null)));

        assertThatThrownBy(() -> service.update(OTHER_ID, 5L, "수정된 내용"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 작성자_본인이면_삭제할_수_있고_게시글_댓글수가_줄어든다() {
        Comment comment = comment(null);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));
        when(commentRepository.countByParentId(5L)).thenReturn(0L);

        service.delete(WRITER_ID, 5L);

        verify(commentRepository).delete(comment);
        verify(postRepository, times(1)).decreaseCommentCount(100L);
    }

    @Test
    void 작성자가_아니어도_관리자면_삭제할_수_있다() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(null)));
        when(commentRepository.countByParentId(5L)).thenReturn(0L);
        doNothing().when(adminGuard).requireAdmin(OTHER_ID);

        assertThatCode(() -> service.delete(OTHER_ID, 5L)).doesNotThrowAnyException();
    }

    @Test
    void 작성자도_관리자도_아니면_삭제시_FORBIDDEN() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(null)));
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(adminGuard).requireAdmin(OTHER_ID);

        assertThatThrownBy(() -> service.delete(OTHER_ID, 5L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(commentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 최상위_댓글을_지우면_딸린_대댓글도_함께_지워지고_댓글수가_그만큼_줄어든다() {
        Comment parent = comment(null);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.countByParentId(5L)).thenReturn(2L); // 대댓글 2개

        service.delete(WRITER_ID, 5L);

        verify(commentRepository).deleteByParentId(5L);
        verify(commentRepository).delete(parent);
        verify(postRepository, times(3)).decreaseCommentCount(100L); // 본인 1 + 대댓글 2
    }

    @Test
    void 대댓글을_지울때는_다른_대댓글을_건드리지_않는다() {
        Comment reply = comment(5L); // parentId = 5L인 대댓글
        ReflectionTestUtils.setField(reply, "id", 6L);
        when(commentRepository.findById(6L)).thenReturn(Optional.of(reply));

        service.delete(WRITER_ID, 6L);

        verify(commentRepository, never()).deleteByParentId(org.mockito.ArgumentMatchers.any());
        verify(commentRepository, never()).countByParentId(org.mockito.ArgumentMatchers.any());
        verify(postRepository, times(1)).decreaseCommentCount(100L);
    }
}
