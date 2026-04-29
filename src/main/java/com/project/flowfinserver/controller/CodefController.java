package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.dto.codef.CodefStockRequest;
import com.project.flowfinserver.service.CodefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "CODEF", description = "CODEF 계정 연결 및 원본 데이터 조회 API")
@RestController
@RequestMapping("/api/codef")
@RequiredArgsConstructor
public class CodefController {

    private final CodefService codefService;

    @Operation(summary = "금융기관 계정 연결", description = "카드/증권 CODEF connectedId를 발급받아 DB에 저장합니다. businessType: CD=카드, ST=증권")
    @PostMapping("/connect")
    public ResponseEntity<String> connectAccount(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @RequestBody CodefConnectRequest request) throws Exception {
        return ResponseEntity.ok(codefService.connectAccount(userId, request));
    }

    @Operation(summary = "증권 계좌번호 등록", description = "연동된 증권 계정에 계좌번호를 등록합니다. 증권 자산 sync 전에 필요합니다.")
    @PatchMapping("/connect/stock/account")
    public ResponseEntity<Void> registerStockAccountNumber(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
            @RequestParam String organization,
            @RequestParam String accountNumber) {
        codefService.registerStockAccountNumber(userId, organization, accountNumber);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "카드 청구 내역 원본 조회", description = "CODEF API로부터 카드 청구 내역 원본 JSON을 반환합니다.")
    @PostMapping("/card/billing")
    public ResponseEntity<String> getCardBilling(@RequestBody CodefCardRequest request) throws Exception {
        return ResponseEntity.ok(codefService.getCardBillingList(request));
    }

    @Operation(summary = "증권 종합자산 원본 조회", description = "CODEF API로부터 증권 종합자산 원본 JSON을 반환합니다.")
    @PostMapping("/stock/assets")
    public ResponseEntity<String> getStockAssets(@RequestBody CodefStockRequest request) throws Exception {
        return ResponseEntity.ok(codefService.getStockAssets(request));
    }
}
