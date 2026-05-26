package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.domain.RiskType;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.user.UpdateProfileRequest;
import com.project.flowfinserver.dto.user.UserProfileResponse;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CodefConnectedAccountRepository codefRepository;
    private final RedisTokenService redisTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ExpenseRepository expenseRepository;
    private final AssetItemRepository assetItemRepository;
    private final AssetAccountRepository assetAccountRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final PortfolioRepository portfolioRepository;
    private final CommunityLikeRepository communityLikeRepository;
    private final CommentRepository commentRepository;
    private final CommunityRepository communityRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        List<CodefConnectedAccount> accounts = codefRepository.findAllByUserIdAndIsActiveTrue(userId);
        return new UserProfileResponse(user, accounts);
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        user.updateProfile(request.getName(), request.getRiskType());

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            log.info("[User] 비밀번호 변경 시도 - userId={}", userId);
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new IllegalArgumentException("현재 비밀번호를 입력해주세요.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                log.warn("[User] 비밀번호 불일치 - userId={}", userId);
                throw new UnauthorizedException("현재 비밀번호가 일치하지 않습니다.");
            }
            user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);
            log.info("[User] 비밀번호 변경 완료 - userId={}", userId);
        }
    }

    @Transactional
    public void updateTendency(Long userId, RiskType riskType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        user.updateRiskType(riskType);
    }

    @Transactional
    public void deleteUser(Long userId, Long requesterId) {
        if (!userId.equals(requesterId)) {
            throw new UnauthorizedException("본인 계정만 탈퇴할 수 있습니다.");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        // 다른 유저가 이 유저의 게시글에 단 댓글/좋아요 먼저 삭제
        communityLikeRepository.deleteAllByCommunityOwnerId(userId);
        commentRepository.deleteAllByCommunityOwnerId(userId);
        // 이 유저가 다른 게시글에 단 댓글/좋아요 삭제
        communityLikeRepository.deleteAllByUserId(userId);
        commentRepository.deleteAllByUserId(userId);
        // 게시글 삭제
        communityRepository.deleteAllByUserId(userId);

        expenseRepository.deleteAllByUserId(userId);
        assetItemRepository.deleteAllByUserId(userId);
        assetAccountRepository.deleteAllByUserId(userId);
        manualAssetRepository.deleteAllByUserId(userId);
        portfolioRepository.deleteAllByUserId(userId);
        codefRepository.deleteAllByUserId(userId);

        redisTokenService.deleteRefreshToken(userId);
        userRepository.deleteById(userId);
    }
}
