package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.CommentRequest;
import com.project.flowfinserver.dto.CommentResponse;
import com.project.flowfinserver.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @RequestBody CommentRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(commentService.createComment(communityId, request, token));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long communityId,
            @PathVariable Long commentId,
            @RequestHeader("Authorization") String token) {
        commentService.deleteComment(communityId, commentId, token);
        return ResponseEntity.noContent().build();
    }
}
