package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.RiskType;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.user.UpdateProfileRequest;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock CodefConnectedAccountRepository codefRepository;
    @Mock RedisTokenService redisTokenService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ExpenseRepository expenseRepository;
    @Mock AssetItemRepository assetItemRepository;
    @Mock AssetAccountRepository assetAccountRepository;
    @Mock ManualAssetRepository manualAssetRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock CommunityLikeRepository communityLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock CommunityRepository communityRepository;

    @InjectMocks
    UserService userService;

    private static final Long USER_ID = 1L;

    private User makeUser() {
        return User.create("encrypted@example.com", "hash123", "encodedPwd", "테스트유저", RiskType.MODERATE);
    }

    // ==================== getMyProfile ====================

    @Nested
    @DisplayName("getMyProfile")
    class getMyProfile {

        @Test
        @DisplayName("존재하지 않는 userId → EntityNotFoundException")
        void 사용자_없음_EntityNotFoundException() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyProfile(USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 호출 시 활성 CODEF 계좌 조회가 호출된다")
        void 활성_CODEF_계좌_조회가_호출된다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(makeUser()));
            given(codefRepository.findAllByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());

            userService.getMyProfile(USER_ID);

            then(codefRepository).should().findAllByUserIdAndIsActiveTrue(USER_ID);
        }
    }

    // ==================== updateProfile ====================

    @Nested
    @DisplayName("updateProfile")
    class updateProfile {

        private User user;

        @BeforeEach
        void setUp() {
            user = makeUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        }

        @Test
        @DisplayName("이름 변경 요청 시 User.name이 새 값으로 반영된다")
        void 이름_변경이_반영된다() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setName("새이름");

            userService.updateProfile(USER_ID, request);

            assertThat(user.getName()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("투자성향 변경 요청 시 User.riskType이 새 값으로 반영된다")
        void 투자성향_변경이_반영된다() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setRiskType(RiskType.AGGRESSIVE);

            userService.updateProfile(USER_ID, request);

            assertThat(user.getRiskType()).isEqualTo(RiskType.AGGRESSIVE);
        }

        @Test
        @DisplayName("newPassword 있는데 currentPassword 누락 → IllegalArgumentException")
        void 새_비밀번호_있는데_현재_비밀번호_누락_IllegalArgumentException() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNewPassword("newPwd123");
            // currentPassword is null

            assertThatThrownBy(() -> userService.updateProfile(USER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("currentPassword 불일치 → UnauthorizedException")
        void 현재_비밀번호_불일치_UnauthorizedException() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNewPassword("newPwd123");
            request.setCurrentPassword("wrongPwd");
            given(passwordEncoder.matches("wrongPwd", "encodedPwd")).willReturn(false);

            assertThatThrownBy(() -> userService.updateProfile(USER_ID, request))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("정상 비밀번호 변경 시 encode 호출 및 userRepository.save 호출된다")
        void 정상_비밀번호_변경시_encode_호출_및_저장된다() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setNewPassword("newPwd123");
            request.setCurrentPassword("currentPwd");
            given(passwordEncoder.matches("currentPwd", "encodedPwd")).willReturn(true);
            given(passwordEncoder.encode("newPwd123")).willReturn("newEncodedPwd");

            userService.updateProfile(USER_ID, request);

            then(passwordEncoder).should().encode("newPwd123");
            then(userRepository).should().save(user);
        }

        @Test
        @DisplayName("newPassword가 없으면 userRepository.save가 호출되지 않는다")
        void 비밀번호_변경_없으면_save_미호출() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setName("변경이름");

            userService.updateProfile(USER_ID, request);

            then(userRepository).should(never()).save(any(User.class));
        }
    }

    // ==================== updateTendency ====================

    @Nested
    @DisplayName("updateTendency")
    class updateTendency {

        @Test
        @DisplayName("존재하지 않는 userId → EntityNotFoundException")
        void 사용자_없음_EntityNotFoundException() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateTendency(USER_ID, RiskType.CONSERVATIVE))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 호출 시 User.riskType이 전달된 값으로 변경된다")
        void 위험성향_변경이_반영된다() {
            User user = makeUser();
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            userService.updateTendency(USER_ID, RiskType.CONSERVATIVE);

            assertThat(user.getRiskType()).isEqualTo(RiskType.CONSERVATIVE);
        }
    }

    // ==================== deleteUser ====================

    @Nested
    @DisplayName("deleteUser")
    class deleteUser {

        @Test
        @DisplayName("userId ≠ requesterId → UnauthorizedException (사용자 조회 전에 검사)")
        void 본인_아닌_경우_UnauthorizedException() {
            assertThatThrownBy(() -> userService.deleteUser(USER_ID, 99L))
                    .isInstanceOf(UnauthorizedException.class);

            then(userRepository).should(never()).findById(anyLong());
        }

        @Test
        @DisplayName("userId == requesterId이지만 사용자 미존재 → EntityNotFoundException")
        void 사용자_없음_EntityNotFoundException() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(USER_ID, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("정상 탈퇴 시 연관 데이터 삭제 메서드들이 순서대로 호출된다")
        void 정상_연관_데이터_삭제_메서드들이_호출된다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(makeUser()));

            userService.deleteUser(USER_ID, USER_ID);

            InOrder inOrder = inOrder(
                    communityLikeRepository, commentRepository, communityRepository,
                    expenseRepository, assetItemRepository, assetAccountRepository,
                    manualAssetRepository, portfolioRepository, codefRepository,
                    redisTokenService, userRepository
            );
            inOrder.verify(communityLikeRepository).deleteAllByCommunityOwnerId(USER_ID);
            inOrder.verify(commentRepository).deleteAllByCommunityOwnerId(USER_ID);
            inOrder.verify(communityLikeRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(commentRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(communityRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(expenseRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(assetItemRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(assetAccountRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(manualAssetRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(portfolioRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(codefRepository).deleteAllByUserId(USER_ID);
            inOrder.verify(redisTokenService).deleteRefreshToken(USER_ID);
            inOrder.verify(userRepository).deleteById(USER_ID);
        }

        @Test
        @DisplayName("정상 탈퇴 시 redisTokenService.deleteRefreshToken(userId)가 호출된다")
        void 정상_Redis_refresh_token_삭제가_호출된다() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(makeUser()));

            userService.deleteUser(USER_ID, USER_ID);

            then(redisTokenService).should().deleteRefreshToken(USER_ID);
        }
    }
}
