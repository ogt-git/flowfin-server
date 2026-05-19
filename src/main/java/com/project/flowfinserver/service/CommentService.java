package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Comment;
import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.CommentRequest;
import com.project.flowfinserver.dto.CommentResponse;
import com.project.flowfinserver.exception.CommentNotFoundException;
import com.project.flowfinserver.exception.CommunityNotFoundException;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.jwt.JwtUtil;
import com.project.flowfinserver.repository.CommentRepository;
import com.project.flowfinserver.repository.CommunityRepository;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AesEncryptionUtil encryptionUtil;

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long communityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return commentRepository.findByCommunityIdAndIsDeletedFalseOrderByCreatedAtAsc(communityId)
                .stream().map(CommentResponse::new).toList();
    }

    @Transactional
    public CommentResponse createComment(Long communityId, CommentRequest request, String token) {
        User user = getUserFromToken(token);
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        Comment comment = Comment.create(community.getId(), user.getId(), request.getContent(), request.isAnonymous());

        return new CommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(Long communityId, Long commentId, String token) {
        User user = getUserFromToken(token);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        if (!comment.getCommunityId().equals(communityId)) {
            throw new CommentNotFoundException(commentId);
        }
        if (!comment.getUserId().equals(user.getId())) {
            throw new UnauthorizedException("댓글 삭제 권한이 없습니다.");
        }

        comment.softDelete();
    }

    private User getUserFromToken(String token) {
        String bearerToken = token.replace("Bearer ", "");
        String email = jwtUtil.getEmail(bearerToken);
        String emailHash = encryptionUtil.hash(email);
        return userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }
}
