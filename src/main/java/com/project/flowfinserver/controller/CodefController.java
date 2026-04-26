package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.service.CodefService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/codef")
@RequiredArgsConstructor
public class CodefController {

    private final CodefService codefService;

    // 금융기관 계정 연결 (connectedId 발급)
    @PostMapping("/connect")
    public ResponseEntity<String> connectAccount(@RequestBody CodefConnectRequest request) throws Exception {
        String result = codefService.connectAccount(request);
        return ResponseEntity.ok(result);
    }

    // 카드 청구 내역 조회
    @PostMapping("/card/billing")
    public ResponseEntity<String> getCardBilling(@RequestBody CodefCardRequest request) throws Exception {
        String result = codefService.getCardBillingList(request);
        return ResponseEntity.ok(result);
    }
}
