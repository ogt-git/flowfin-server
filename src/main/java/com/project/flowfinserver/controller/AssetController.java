package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.asset.AssetSummaryResponse;
import com.project.flowfinserver.dto.asset.ManualAssetRequest;
import com.project.flowfinserver.dto.asset.ManualAssetResponse;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.service.AssetService;
import com.project.flowfinserver.service.ManualAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Asset", description = "자산 조회·수동입력 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final ManualAssetService manualAssetService;

    @Operation(summary = "증권 자산 조회", description = "연동된 증권 계좌와 보유 종목 목록을 반환합니다. 계좌번호는 마스킹 처리됩니다.")
    @GetMapping("/stocks")
    public ResponseEntity<ApiResponse<List<StockAccountResponse>>> getStocks(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(assetService.getStocks(userId)));
    }

    @Operation(summary = "총 자산 요약", description = "증권 자산·수동 자산을 합산하고 투자 가능 금액을 산출합니다.")
    @GetMapping("/assets/summary")
    public ResponseEntity<ApiResponse<AssetSummaryResponse>> getAssetSummary(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(assetService.getAssetSummary(userId)));
    }

    @Operation(summary = "수동 자산 입력", description = "예금·적금·부동산·현금·기타 자산을 직접 입력합니다. 유동 자산(예금·적금·현금)은 투자 가능 금액에 반영됩니다.")
    @PostMapping("/assets/manual")
    public ResponseEntity<ApiResponse<ManualAssetResponse>> saveManualAsset(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ManualAssetRequest request) {
        ManualAssetResponse response = manualAssetService.save(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "수동 자산이 등록되었습니다."));
    }

    @Operation(summary = "수동 자산 목록 조회", description = "사용자가 입력한 수동 자산 전체 목록을 반환합니다.")
    @GetMapping("/assets/manual")
    public ResponseEntity<ApiResponse<List<ManualAssetResponse>>> getManualAssets(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(manualAssetService.getAll(userId)));
    }

    @Operation(summary = "수동 자산 수정", description = "입력한 수동 자산을 수정합니다. 수정 즉시 투자 가능 금액이 재산출됩니다.")
    @PutMapping("/assets/manual/{assetId}")
    public ResponseEntity<ApiResponse<ManualAssetResponse>> updateManualAsset(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody ManualAssetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(manualAssetService.update(userId, assetId, request)));
    }

    @Operation(summary = "수동 자산 삭제", description = "입력한 수동 자산을 삭제합니다. 삭제 즉시 투자 가능 금액이 재산출됩니다.")
    @DeleteMapping("/assets/manual/{assetId}")
    public ResponseEntity<ApiResponse<Void>> deleteManualAsset(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long assetId) {
        manualAssetService.delete(userId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null, "수동 자산이 삭제되었습니다."));
    }
}
