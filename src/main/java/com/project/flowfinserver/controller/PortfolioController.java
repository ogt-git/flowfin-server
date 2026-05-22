package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.PortfolioResponse;
import com.project.flowfinserver.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Portfolio", description = "포트폴리오 API")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(summary = "최신 포트폴리오 조회", description = "가장 최근에 추천된 포트폴리오를 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> getLatest(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(ApiResponse.success(portfolioService.getLatest(userId)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.success(null, "추천된 포트폴리오가 없습니다."));
        }
    }

    @Operation(summary = "포트폴리오 이력 조회", description = "마이페이지용 포트폴리오 추천 이력을 반환합니다.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> getHistory(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(portfolioService.getHistory(userId)));
    }
}
