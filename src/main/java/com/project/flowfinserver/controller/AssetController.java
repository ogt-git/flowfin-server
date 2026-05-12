package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Asset", description = "자산 조회 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(summary = "증권 자산 조회", description = "연동된 증권 계좌와 보유 종목 목록을 반환합니다. 계좌번호는 마스킹 처리됩니다.")
    @GetMapping("/stocks")
    public ResponseEntity<ApiResponse<List<StockAccountResponse>>> getStocks(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(assetService.getStocks(userId)));
    }
}
