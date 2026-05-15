package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.dto.codef.CodefStockRequest;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.exception.ErrorCode;
import com.project.flowfinserver.exception.TooManyRequestsException;
import com.project.flowfinserver.service.CodefService;
import com.project.flowfinserver.service.CodefSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "CODEF", description = "CODEF 계정 연결 및 금융 데이터 동기화 API")
@RestController
@RequestMapping("/api/codef")
@RequiredArgsConstructor
public class CodefController {

    private final CodefService codefService;
    private final CodefSyncService codefSyncService;

    @Operation(summary = "금융기관 계정 연결", description = "카드/증권 CODEF connectedId를 발급받아 DB에 저장합니다. businessType: CD=카드, ST=증권")
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<String>> connectAccount(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @RequestBody CodefConnectRequest request) throws Exception {
        String response = codefService.connectAccount(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "계정 연결이 완료되었습니다."));
    }

    @Operation(summary = "연동 해지", description = "연동된 카드/증권 계정을 해지합니다.")
    @DeleteMapping("/connect/{id}")
    public ResponseEntity<ApiResponse<Void>> disconnectAccount(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) throws Exception {
        codefService.disconnect(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "연동이 해지되었습니다."));
    }

    @Operation(summary = "카드 내역 수동 새로고침", description = "카드 청구 내역을 즉시 동기화합니다. 5분에 한 번만 가능합니다.")
    @PostMapping("/sync/card")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> manualSyncCard(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        try {
            CodefSyncResultDto result = codefSyncService.manualSyncCard(userId);
            if (!result.getFailedAccounts().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiResponse.error("일부 카드 계정 동기화에 실패했습니다.", ErrorCode.CODEF_SYNC_FAILED.name()));
            }
            String message = result.getSavedCount() == 0 ? "조회된 청구 내역이 없습니다." : "카드 내역 동기화가 완료되었습니다.";
            return ResponseEntity.ok(ApiResponse.success(result, message));
        } catch (TooManyRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(e.getMessage(), "CODEF_COOLDOWN_ACTIVE"));
        }
    }

    @Operation(summary = "증권 자산 수동 새로고침", description = "증권 종합자산을 즉시 동기화합니다. 5분에 한 번만 가능합니다.")
    @PostMapping("/sync/stock")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> manualSyncStock(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        try {
            CodefSyncResultDto result = codefSyncService.manualSyncStock(userId);
            if (!result.getFailedAccounts().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiResponse.error("일부 증권 계좌 동기화에 실패했습니다.", ErrorCode.CODEF_SYNC_FAILED.name()));
            }
            String message = result.getSavedCount() == 0 ? "업데이트된 자산 정보가 없습니다." : "자산 정보 동기화가 완료되었습니다.";
            return ResponseEntity.ok(ApiResponse.success(result, message));
        } catch (TooManyRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(e.getMessage(), "CODEF_COOLDOWN_ACTIVE"));
        }
    }

    // TODO: 하위 호환 유지용 — /sync/card 와 /sync/stock 으로 마이그레이션 완료 후 제거할 것
    @Operation(summary = "수동 새로고침 (deprecated)", description = "[deprecated] /sync/card 및 /sync/stock을 사용하세요.")
    @PostMapping("/sync")
    @Deprecated
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> manualSync(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId) {
        CodefSyncResultDto result = codefSyncService.manualSync(userId);
        if (!result.getFailedAccounts().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("일부 계정 동기화에 실패했습니다.", ErrorCode.CODEF_SYNC_FAILED.name()));
        }
        String message = result.getSavedCount() == 0 ? "조회된 청구 내역이 없습니다." : "동기화가 완료되었습니다.";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    @Operation(summary = "카드 청구 내역 수집 (내부 전용)", description = "CODEF API로 카드 청구 내역을 조회하고 Expense DB에 저장합니다.")
    @PostMapping("/card")
    public ResponseEntity<ApiResponse<CodefSyncResultDto>> syncCard(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CodefCardRequest request) throws Exception {
        String rawResponse = codefService.getCardBillingList(request);
        CodefSyncResultDto result = codefSyncService.saveFromRawResponse(
                userId, request.getConnectedId(), request.getOrganization(), rawResponse);
        String message = result.getSavedCount() == 0 ? "조회된 청구 내역이 없습니다." : "요청이 성공적으로 처리되었습니다.";
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    @Operation(summary = "증권 종합자산 수집 (내부 전용)", description = "CODEF API로부터 증권 종합자산을 조회하고 저장합니다.")
    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<String>> syncStock(
            @RequestBody CodefStockRequest request) throws Exception {
        String rawResponse = codefService.getStockAssets(request);
        return ResponseEntity.ok(ApiResponse.success(rawResponse));
    }
}
