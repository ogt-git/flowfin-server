package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.service.CodefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CODEF", description = "CODEF 계정 연결 및 원본 데이터 조회 API")
@RestController
@RequestMapping("/api/codef")
@RequiredArgsConstructor
public class CodefController {

    private final CodefService codefService;

    @Operation(summary = "금융기관 계정 연결", description = "CODEF connectedId를 발급받아 DB에 저장합니다.")
    @PostMapping("/connect")
    public ResponseEntity<String> connectAccount(@RequestBody CodefConnectRequest request) throws Exception {
        String result = codefService.connectAccount(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "카드 청구 내역 원본 조회", description = "CODEF API로부터 카드 청구 내역 원본 JSON을 반환합니다.")
    @PostMapping("/card/billing")
    public ResponseEntity<String> getCardBilling(@RequestBody CodefCardRequest request) throws Exception {
        String result = codefService.getCardBillingList(request);
        return ResponseEntity.ok(result);
    }
}
