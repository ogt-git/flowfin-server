package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.domain.CommunityLike;
import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.CommunityRequest;
import com.project.flowfinserver.dto.CommunityResponse;
import com.project.flowfinserver.dto.LikeResponse;
import com.project.flowfinserver.dto.PortfolioShareRequest;
import com.project.flowfinserver.exception.CommunityNotFoundException;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.CommentRepository;
import com.project.flowfinserver.repository.CommunityLikeRepository;
import com.project.flowfinserver.repository.CommunityRepository;
import com.project.flowfinserver.repository.PortfolioRepository;
import com.project.flowfinserver.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityLikeRepository communityLikeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;

    private String resolveAuthorName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse("알 수 없음");
    }

    @Transactional(readOnly = true)
    public List<CommunityResponse> getPosts(String category, String keyword) {
        List<Community> posts;

        if (keyword != null && !keyword.isBlank()) {
            posts = communityRepository
                    .findAllByTitleContainingOrContentContainingOrderByCreatedAtDesc(keyword, keyword);
        } else if (category != null && !category.isBlank() && !category.equals("전체")) {
            posts = communityRepository.findAllByCategoryOrderByCreatedAtDesc(category);
        } else {
            posts = communityRepository.findAllByOrderByCreatedAtDesc();
        }

        return posts.stream()
                .map(post -> new CommunityResponse(post, resolveAuthorName(post.getUserId()),
                        commentRepository.countByCommunityId(post.getId())))
                .toList();
    }

    @Transactional
    public CommunityResponse getPost(Long id) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));
        community.increaseViews();
        return new CommunityResponse(community, resolveAuthorName(community.getUserId()),
                commentRepository.countByCommunityId(id));
    }

    @Transactional
    public CommunityResponse createPost(CommunityRequest request, Long userId) {
        Community community = Community.create(userId, request.getTitle(), request.getContent(), request.getCategory());
        return new CommunityResponse(communityRepository.save(community), resolveAuthorName(userId), 0);
    }

    @Transactional
    public CommunityResponse updatePost(Long id, CommunityRequest request, Long userId) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));

        if (!community.getUserId().equals(userId)) {
            throw new UnauthorizedException("수정 권한이 없습니다.");
        }

        community.update(request.getTitle(), request.getContent());
        return new CommunityResponse(community, resolveAuthorName(userId),
                commentRepository.countByCommunityId(id));
    }

    @Transactional
    public void deletePost(Long id, Long userId) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new CommunityNotFoundException(id));

        if (!community.getUserId().equals(userId)) {
            throw new UnauthorizedException("삭제 권한이 없습니다.");
        }

        commentRepository.deleteAllByCommunityId(id);
        communityLikeRepository.deleteAllByCommunityId(id);
        communityRepository.delete(community);
    }

    @Transactional
    public LikeResponse toggleLike(Long communityId, Long userId) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // 중복 좋아요 시 DB 유니크 제약조건(uq_like)이 DataIntegrityViolationException → 409 반환
        communityLikeRepository.save(CommunityLike.create(userId, communityId));
        community.increaseLikeCount();
        return new LikeResponse(true, community.getLikeCount());
    }

    @Transactional
    public CommunityResponse sharePortfolio(PortfolioShareRequest request, Long userId) {
        String content = request.getContent() != null ? request.getContent() : "";
        if (request.getPortfolioId() != null) {
            Portfolio portfolio = portfolioRepository.findById(request.getPortfolioId())
                    .orElseThrow(() -> new EntityNotFoundException("포트폴리오를 찾을 수 없습니다."));
            if (!portfolio.getUserId().equals(userId)) {
                throw new UnauthorizedException("본인 포트폴리오만 공유할 수 있습니다.");
            }
            content = "[포트폴리오 공유]\n투자가능금액: " + portfolio.getInvestableAmount() + "원\n" + content;
        }

        String title = request.getTitle() != null ? request.getTitle() : "포트폴리오를 공유합니다.";
        Community community = Community.create(userId, title, content, "PORTFOLIO");
        return new CommunityResponse(communityRepository.save(community), resolveAuthorName(userId), 0);
    }
}
