package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Comment;
import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.dto.CommentRequest;
import com.project.flowfinserver.exception.CommentNotFoundException;
import com.project.flowfinserver.exception.CommunityNotFoundException;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.CommentRepository;
import com.project.flowfinserver.repository.CommunityRepository;
import com.project.flowfinserver.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock CommunityRepository communityRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    CommentService commentService;

    private static final Long USER_ID = 1L;
    private static final Long COMMUNITY_ID = 10L;
    private static final Long COMMENT_ID = 200L;

    private Community newCommunity() {
        Community c = Community.create(USER_ID, "제목", "내용", "FREE");
        ReflectionTestUtils.setField(c, "id", COMMUNITY_ID);
        return c;
    }

    private Comment newComment(Long communityId, Long userId) {
        Comment c = Comment.create(communityId, userId, "댓글내용", false);
        ReflectionTestUtils.setField(c, "createdAt", LocalDateTime.now());
        return c;
    }

    // ==================== getComments ====================

    @Nested
    @DisplayName("getComments")
    class getComments {

        @Test
        @DisplayName("존재하지 않는 communityId → CommunityNotFoundException")
        void 존재하지_않는_communityId_CommunityNotFoundException() {
            given(communityRepository.existsById(COMMUNITY_ID)).willReturn(false);

            assertThatThrownBy(() -> commentService.getComments(COMMUNITY_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 조회 시 commentRepository.findByCommunityIdOrderByCreatedAtAsc가 호출된다")
        void 정상_조회_시_commentRepository_조회가_호출된다() {
            given(communityRepository.existsById(COMMUNITY_ID)).willReturn(true);
            given(commentRepository.findByCommunityIdOrderByCreatedAtAsc(COMMUNITY_ID))
                    .willReturn(List.of());

            commentService.getComments(COMMUNITY_ID);

            then(commentRepository).should().findByCommunityIdOrderByCreatedAtAsc(COMMUNITY_ID);
        }

        @Test
        @DisplayName("getComments는 communityRepository.findById가 아닌 existsById를 사용한다")
        void existsById로_게시글_존재여부를_확인한다() {
            given(communityRepository.existsById(COMMUNITY_ID)).willReturn(true);
            given(commentRepository.findByCommunityIdOrderByCreatedAtAsc(COMMUNITY_ID))
                    .willReturn(List.of());

            commentService.getComments(COMMUNITY_ID);

            then(communityRepository).should().existsById(COMMUNITY_ID);
            then(communityRepository).should(never()).findById(any());
        }
    }

    // ==================== createComment ====================

    @Nested
    @DisplayName("createComment")
    class createComment {

        @Test
        @DisplayName("존재하지 않는 communityId → CommunityNotFoundException")
        void 존재하지_않는_communityId_CommunityNotFoundException() {
            given(communityRepository.findById(COMMUNITY_ID)).willReturn(Optional.empty());
            CommentRequest request = mock(CommentRequest.class);

            assertThatThrownBy(() -> commentService.createComment(COMMUNITY_ID, request, USER_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 저장 시 commentRepository.save()가 1회 호출된다")
        void 정상_저장_시_save가_호출된다() {
            given(communityRepository.findById(COMMUNITY_ID)).willReturn(Optional.of(newCommunity()));
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            CommentRequest request = mock(CommentRequest.class);
            given(request.getContent()).willReturn("댓글내용");
            given(request.isAnonymous()).willReturn(false);

            Comment saved = newComment(COMMUNITY_ID, USER_ID);
            given(commentRepository.save(any(Comment.class))).willReturn(saved);

            commentService.createComment(COMMUNITY_ID, request, USER_ID);

            then(commentRepository).should().save(any(Comment.class));
        }
    }

    // ==================== deleteComment ====================

    @Nested
    @DisplayName("deleteComment")
    class deleteComment {

        @Test
        @DisplayName("존재하지 않는 commentId → CommentNotFoundException")
        void 존재하지_않는_commentId_CommentNotFoundException() {
            given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> commentService.deleteComment(COMMUNITY_ID, COMMENT_ID, USER_ID))
                    .isInstanceOf(CommentNotFoundException.class);
        }

        @Test
        @DisplayName("댓글의 communityId가 요청 communityId와 다르면 CommentNotFoundException")
        void communityId_불일치_CommentNotFoundException() {
            Comment comment = newComment(999L, USER_ID);
            given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(COMMUNITY_ID, COMMENT_ID, USER_ID))
                    .isInstanceOf(CommentNotFoundException.class);
        }

        @Test
        @DisplayName("댓글 작성자가 아닌 userId로 삭제 요청 → UnauthorizedException")
        void 작성자_아닌_유저_UnauthorizedException() {
            Comment comment = newComment(COMMUNITY_ID, USER_ID);
            given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(COMMUNITY_ID, COMMENT_ID, 99L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("정상 삭제 시 comment.isDeleted가 true로 변경된다 (소프트 삭제)")
        void 정상_삭제_시_isDeleted가_true가_된다() {
            Comment comment = newComment(COMMUNITY_ID, USER_ID);
            given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

            commentService.deleteComment(COMMUNITY_ID, COMMENT_ID, USER_ID);

            assertThat(comment.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("정상 삭제 시 commentRepository.delete()가 호출되지 않는다 (물리 삭제 금지)")
        void 정상_삭제_시_delete가_호출되지_않는다() {
            Comment comment = newComment(COMMUNITY_ID, USER_ID);
            given(commentRepository.findById(COMMENT_ID)).willReturn(Optional.of(comment));

            commentService.deleteComment(COMMUNITY_ID, COMMENT_ID, USER_ID);

            then(commentRepository).should(never()).delete(any(Comment.class));
        }
    }
}
