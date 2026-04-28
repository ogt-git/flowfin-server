package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.service.CodefSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "CODEF Sync", description = "CODEF 금융 데이터 동기화 API")
@RestController
@RequestMapping("/api/v1/codef/sync")
@RequiredArgsConstructor
public class CodefSyncController {

    private final CodefSyncService codefSyncService;

    @Operation(summary = "카드 지출 내역 동기화", description = "CODEF를 통해 최근 30일 카드 청구 내역을 조회하고 DB에 저장합니다.")
    @PostMapping("/card")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> syncCard(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(codefSyncService.syncCard(userId)));
    }

    @Operation(summary = "증권 자산 스냅샷 동기화", description = "CODEF를 통해 오늘 기준 증권 종합자산을 조회하고 DB에 저장합니다.")
    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> syncStock(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(codefSyncService.syncStock(userId)));
    }
}
