package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.CommunityRequest;
import com.project.flowfinserver.dto.CommunityResponse;
import com.project.flowfinserver.jwt.JwtUtil;
import com.project.flowfinserver.repository.CommunityRepository;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AesEncryptionUtil encryptionUtil;

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

        return posts.stream().map(CommunityResponse::new).toList();
    }

    @Transactional
    public CommunityResponse getPost(Long id) {
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        community.increaseViews();
        return new CommunityResponse(community);
    }

    @Transactional
    public CommunityResponse createPost(CommunityRequest request, String token) {
        User user = getUserFromToken(token);
        Community community = Community.builder()
                .author(user)
                .title(request.getTitle())
                .content(request.getContent())
                .category(request.getCategory())
                .build();
        return new CommunityResponse(communityRepository.save(community));
    }

    @Transactional
    public CommunityResponse updatePost(Long id, CommunityRequest request, String token) {
        User user = getUserFromToken(token);
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (!community.getAuthor().getId().equals(user.getId())) {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        community.update(request.getTitle(), request.getContent(), request.getCategory());
        return new CommunityResponse(community);
    }

    @Transactional
    public void deletePost(Long id, String token) {
        User user = getUserFromToken(token);
        Community community = communityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (!community.getAuthor().getId().equals(user.getId())) {
            throw new RuntimeException("삭제 권한이 없습니다.");
        }

        communityRepository.delete(community);
    }

    private User getUserFromToken(String token) {
        String bearerToken = token.replace("Bearer ", "");
        String email = jwtUtil.getEmail(bearerToken);
        String emailHash = encryptionUtil.hash(email);
        return userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }
}