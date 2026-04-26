package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.service.CodefSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// security.UserPrincipal 의존 제거 — X-User-Id 헤더로 userId 수신 (JWT 필터가 토큰 검증 담당)
@RestController
@RequestMapping("/api/v1/codef/sync")
@RequiredArgsConstructor
public class CodefSyncController {

    private final CodefSyncService codefSyncService;

    // 카드 지출 내역 동기화 (최근 30일, CODEF 승인내역 기준)
    @PostMapping("/card")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> syncCard(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(codefSyncService.syncCard(userId)));
    }

    // 증권 종합자산 스냅샷 동기화 (오늘 기준)
    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> syncStock(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(codefSyncService.syncStock(userId)));
    }
}
