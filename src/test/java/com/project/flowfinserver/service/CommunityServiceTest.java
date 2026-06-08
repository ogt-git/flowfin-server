package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.RiskType;
import com.project.flowfinserver.dto.CommunityRequest;
import com.project.flowfinserver.dto.CommunityResponse;
import com.project.flowfinserver.dto.LikeResponse;
import com.project.flowfinserver.dto.PortfolioShareRequest;
import com.project.flowfinserver.exception.CommunityNotFoundException;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceTest {

    @Mock CommunityRepository communityRepository;
    @Mock CommunityLikeRepository communityLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock PortfolioRepository portfolioRepository;

    @InjectMocks
    CommunityService communityService;

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 100L;

    private Community newPost(Long userId) {
        Community c = Community.create(userId, "제목", "내용", "FREE");
        ReflectionTestUtils.setField(c, "createdAt", LocalDateTime.now());
        return c;
    }

    // ==================== getPosts ====================

    @Nested
    @DisplayName("getPosts")
    class getPosts {

        @Test
        @DisplayName("keyword가 있으면 제목·내용 통합 검색 쿼리가 호출된다")
        void keyword_있음_제목내용검색_쿼리가_호출된다() {
            given(communityRepository
                    .findAllByTitleContainingOrContentContainingOrderByCreatedAtDesc("스프링", "스프링"))
                    .willReturn(List.of());

            communityService.getPosts(null, "스프링");

            then(communityRepository).should()
                    .findAllByTitleContainingOrContentContainingOrderByCreatedAtDesc("스프링", "스프링");
            then(communityRepository).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("특정 category가 있고 keyword 없으면 카테고리별 쿼리가 호출된다")
        void category_특정값_카테고리별_쿼리가_호출된다() {
            given(communityRepository.findAllByCategoryOrderByCreatedAtDesc("PORTFOLIO"))
                    .willReturn(List.of());

            communityService.getPosts("PORTFOLIO", null);

            then(communityRepository).should().findAllByCategoryOrderByCreatedAtDesc("PORTFOLIO");
        }

        @Test
        @DisplayName("category가 '전체'이면 전체 목록 쿼리가 호출된다")
        void category_전체_전체목록_쿼리가_호출된다() {
            given(communityRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

            communityService.getPosts("전체", null);

            then(communityRepository).should().findAllByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("category가 null이면 전체 목록 쿼리가 호출된다")
        void category_null_전체목록_쿼리가_호출된다() {
            given(communityRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

            communityService.getPosts(null, null);

            then(communityRepository).should().findAllByOrderByCreatedAtDesc();
        }
    }

    // ==================== getPost ====================

    @Nested
    @DisplayName("getPost")
    class getPost {

        @Test
        @DisplayName("존재하지 않는 id → CommunityNotFoundException")
        void 존재하지_않는_id_CommunityNotFoundException() {
            given(communityRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityService.getPost(POST_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 조회 시 community.views가 1 증가한다")
        void 정상_조회_시_views가_1_증가한다() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            communityService.getPost(POST_ID);

            assertThat(community.getViews()).isEqualTo(1);
        }
    }

    // ==================== createPost ====================

    @Nested
    @DisplayName("createPost")
    class createPost {

        @Test
        @DisplayName("정상 저장 시 communityRepository.save()가 호출된다")
        void 정상_저장_시_communityRepository_save가_호출된다() {
            CommunityRequest request = mock(CommunityRequest.class);
            given(request.getTitle()).willReturn("제목");
            given(request.getContent()).willReturn("내용");
            given(request.getCategory()).willReturn("FREE");

            Community saved = newPost(USER_ID);
            given(communityRepository.save(any(Community.class))).willReturn(saved);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            communityService.createPost(request, USER_ID);

            then(communityRepository).should().save(any(Community.class));
        }
    }

    // ==================== updatePost ====================

    @Nested
    @DisplayName("updatePost")
    class updatePost {

        @Test
        @DisplayName("존재하지 않는 id → CommunityNotFoundException")
        void 존재하지_않는_id_CommunityNotFoundException() {
            given(communityRepository.findById(POST_ID)).willReturn(Optional.empty());
            CommunityRequest request = mock(CommunityRequest.class);

            assertThatThrownBy(() -> communityService.updatePost(POST_ID, request, USER_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("작성자가 아닌 userId로 수정 요청 → UnauthorizedException")
        void 다른_유저_UnauthorizedException() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));
            CommunityRequest request = mock(CommunityRequest.class);

            assertThatThrownBy(() -> communityService.updatePost(POST_ID, request, 99L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("정상 수정 시 community의 title·content가 새 값으로 반영된다")
        void 정상_수정_시_title_content가_반영된다() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            CommunityRequest request = mock(CommunityRequest.class);
            given(request.getTitle()).willReturn("수정제목");
            given(request.getContent()).willReturn("수정내용");

            communityService.updatePost(POST_ID, request, USER_ID);

            assertThat(community.getTitle()).isEqualTo("수정제목");
            assertThat(community.getContent()).isEqualTo("수정내용");
        }
    }

    // ==================== deletePost ====================

    @Nested
    @DisplayName("deletePost")
    class deletePost {

        @Test
        @DisplayName("존재하지 않는 id → CommunityNotFoundException")
        void 존재하지_않는_id_CommunityNotFoundException() {
            given(communityRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityService.deletePost(POST_ID, USER_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("작성자가 아닌 userId로 삭제 요청 → UnauthorizedException")
        void 다른_유저_UnauthorizedException() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));

            assertThatThrownBy(() -> communityService.deletePost(POST_ID, 99L))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("정상 삭제 시 댓글→좋아요→게시글 순으로 삭제된다")
        void 정상_삭제_시_삭제_순서가_올바르다() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));

            communityService.deletePost(POST_ID, USER_ID);

            InOrder inOrder = inOrder(commentRepository, communityLikeRepository, communityRepository);
            inOrder.verify(commentRepository).deleteAllByCommunityId(POST_ID);
            inOrder.verify(communityLikeRepository).deleteAllByCommunityId(POST_ID);
            inOrder.verify(communityRepository).delete(community);
        }
    }

    // ==================== toggleLike ====================

    @Nested
    @DisplayName("toggleLike")
    class toggleLike {

        @Test
        @DisplayName("존재하지 않는 communityId → CommunityNotFoundException")
        void 존재하지_않는_id_CommunityNotFoundException() {
            given(communityRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityService.toggleLike(POST_ID, USER_ID))
                    .isInstanceOf(CommunityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 좋아요 시 community.likeCount가 1 증가하고 save가 호출된다")
        void 정상_likeCount_증가_및_save_호출() {
            Community community = newPost(USER_ID);
            given(communityRepository.findById(POST_ID)).willReturn(Optional.of(community));

            LikeResponse result = communityService.toggleLike(POST_ID, USER_ID);

            then(communityLikeRepository).should().save(any());
            assertThat(community.getLikeCount()).isEqualTo(1);
            assertThat(result.getLikeCount()).isEqualTo(1);
            assertThat(result.isLiked()).isTrue();
        }
    }

    // ==================== sharePortfolio ====================

    @Nested
    @DisplayName("sharePortfolio")
    class sharePortfolio {

        @Test
        @DisplayName("portfolioId 없고 title 없으면 기본 제목으로 게시글이 생성된다")
        void portfolioId_없음_기본_제목_적용() {
            PortfolioShareRequest request = mock(PortfolioShareRequest.class);
            given(request.getPortfolioId()).willReturn(null);
            given(request.getTitle()).willReturn(null);
            given(request.getContent()).willReturn(null);

            Community saved = newPost(USER_ID);
            given(communityRepository.save(any(Community.class))).willReturn(saved);
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            communityService.sharePortfolio(request, USER_ID);

            // save 호출 시 전달된 Community의 title이 기본값인지 확인
            org.mockito.ArgumentCaptor<Community> captor =
                    org.mockito.ArgumentCaptor.forClass(Community.class);
            then(communityRepository).should().save(captor.capture());
            assertThat(captor.getValue().getTitle()).isEqualTo("포트폴리오를 공유합니다.");
        }

        @Test
        @DisplayName("portfolioId 있지만 포트폴리오 미존재 → EntityNotFoundException")
        void portfolioId_있지만_포트폴리오_미존재_EntityNotFoundException() {
            PortfolioShareRequest request = mock(PortfolioShareRequest.class);
            given(request.getPortfolioId()).willReturn(42);
            given(request.getContent()).willReturn(null);
            given(portfolioRepository.findById(42)).willReturn(Optional.empty());

            assertThatThrownBy(() -> communityService.sharePortfolio(request, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("타인 portfolioId 사용 → UnauthorizedException")
        void 타인_포트폴리오_UnauthorizedException() {
            PortfolioShareRequest request = mock(PortfolioShareRequest.class);
            given(request.getPortfolioId()).willReturn(42);
            given(request.getContent()).willReturn(null);

            Portfolio portfolio = mock(Portfolio.class);
            given(portfolio.getUserId()).willReturn(99L);
            given(portfolioRepository.findById(42)).willReturn(Optional.of(portfolio));

            assertThatThrownBy(() -> communityService.sharePortfolio(request, USER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
