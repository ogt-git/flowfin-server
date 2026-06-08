package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Comment;
import com.project.flowfinserver.domain.Community;
import com.project.flowfinserver.dto.CommentRequest;
import com.project.flowfinserver.dto.CommentResponse;
import com.project.flowfinserver.exception.CommentNotFoundException;
import com.project.flowfinserver.exception.CommunityNotFoundException;
import com.project.flowfinserver.exception.UnauthorizedException;
import com.project.flowfinserver.repository.CommentRepository;
import com.project.flowfinserver.repository.CommunityRepository;
import com.project.flowfinserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;

    private String resolveAuthorName(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getName())
                .orElse("알 수 없음");
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long communityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        List<Comment> comments = commentRepository.findByCommunityIdOrderByCreatedAtAsc(communityId);
        log.info("[Comment] GET communityId={} → {}건", communityId, comments.size());
        return comments.stream()
                .map(c -> new CommentResponse(c, resolveAuthorName(c.getUserId())))
                .toList();
    }

    @Transactional
    public CommentResponse createComment(Long communityId, CommentRequest request, Long userId) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        Comment comment = Comment.create(community.getId(), userId, request.getContent(), request.isAnonymous());
        Comment saved = commentRepository.save(comment);
        log.info("[Comment] POST 저장 - id={}, communityId={}, userId={}", saved.getId(), saved.getCommunityId(), saved.getUserId());
        return new CommentResponse(saved, resolveAuthorName(userId));
    }

    @Transactional
    public void deleteComment(Long communityId, Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        if (!comment.getCommunityId().equals(communityId)) {
            throw new CommentNotFoundException(commentId);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new UnauthorizedException("댓글 삭제 권한이 없습니다.");
        }

        comment.softDelete();
    }
}
