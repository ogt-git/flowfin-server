package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.CommentRequest;
import com.project.flowfinserver.dto.CommentResponse;
import com.project.flowfinserver.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community/{communityId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long communityId) {
        return ResponseEntity.ok(commentService.getComments(communityId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long communityId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(commentService.createComment(communityId, request, userId));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long communityId,
            @PathVariable Long commentId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        commentService.deleteComment(communityId, commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
