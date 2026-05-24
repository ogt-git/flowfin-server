package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioHistoryResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioRecommendResult;
import com.project.flowfinserver.dto.portfolio.PortfolioStatusResponse;
import com.project.flowfinserver.service.PortfolioFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Portfolio", description = "AI 포트폴리오 추천 API")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioFacadeService portfolioFacadeService;

    @Operation(
            summary = "포트폴리오 추천 생성",
            description = """
                    투자 성향(risk_type)과 자산 연동 상태를 검증한 뒤 AI 포트폴리오 추천을 비동기로 시작합니다.
                    - 자산 미연동: 200 + needAssetLink=true
                    - 24h 캐시 히트(성향·자산 배분 동일): 200 + 기존 결과 즉시 반환
                    - 정상 시작: 202 + portfolioId (GET /api/portfolio로 폴링)
                    - 중복 요청 중: 429
                    - 5분 쿨다운: 429
                    """
    )
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<?>> recommend(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        PortfolioRecommendResult result = portfolioFacadeService.requestRecommend(userId);

        return switch (result.type()) {
            case NEED_ASSET_LINK -> ResponseEntity.ok(
                    ApiResponse.success(Map.of("needAssetLink", true),
                            "증권 계좌를 먼저 연동해주세요."));

            case CACHE_HIT -> ResponseEntity.ok(
                    ApiResponse.success(result.cachedResponse(),
                            "24시간 이내 동일 조건의 추천 결과를 반환합니다."));

            case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    ApiResponse.success(Map.of("portfolioId", result.portfolioId()),
                            "포트폴리오 추천을 시작했습니다. GET /api/portfolio 로 결과를 확인하세요."));
        };
    }

    @Operation(
            summary = "포트폴리오 최신 결과 조회",
            description = """
                    현재 사용자의 최신 포트폴리오 추천 결과를 반환합니다.
                    - status=PENDING: 생성 중 (프론트 5초 폴링 권장)
                    - status=COMPLETED: 추천 완료, result 필드에 상세 데이터 포함
                    - status=FAILED: 추천 실패
                    - 이력 없음: canRecommend=true, status=null
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioStatusResponse>> getLatest(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PortfolioStatusResponse response = portfolioFacadeService.getLatestPortfolio(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "포트폴리오 추천 이력 조회",
            description = "사용자의 포트폴리오 추천 이력을 최신순으로 반환합니다. (마이페이지용)"
    )
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PortfolioHistoryResponse>>> getHistory(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<PortfolioHistoryResponse> history = portfolioFacadeService.getPortfolioHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
