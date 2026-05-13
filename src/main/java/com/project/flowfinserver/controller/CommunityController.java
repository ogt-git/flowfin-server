package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.CommunityRequest;
import com.project.flowfinserver.dto.CommunityResponse;
import com.project.flowfinserver.dto.LikeResponse;
import com.project.flowfinserver.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public ResponseEntity<List<CommunityResponse>> getPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(communityService.getPosts(category, keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(communityService.getPost(id));
    }

    @PostMapping
    public ResponseEntity<CommunityResponse> createPost(
            @RequestBody CommunityRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(communityService.createPost(request, token));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommunityResponse> updatePost(
            @PathVariable Long id,
            @RequestBody CommunityRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(communityService.updatePost(id, request, token));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        communityService.deletePost(id, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/like/{id}")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(communityService.toggleLike(id, token));
    }
}