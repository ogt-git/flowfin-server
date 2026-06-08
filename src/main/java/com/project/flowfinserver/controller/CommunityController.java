package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.CommunityRequest;
import com.project.flowfinserver.dto.CommunityResponse;
import com.project.flowfinserver.dto.LikeResponse;
import com.project.flowfinserver.dto.PortfolioShareRequest;
import com.project.flowfinserver.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            @Valid @RequestBody CommunityRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(communityService.createPost(request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommunityResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody CommunityRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(communityService.updatePost(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        communityService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/like/{id}")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(communityService.toggleLike(id, userId));
    }

    @PostMapping("/portfolio")
    public ResponseEntity<CommunityResponse> sharePortfolio(
            @RequestBody PortfolioShareRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(communityService.sharePortfolio(request, userId));
    }
}
