package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.exchangerate.ExchangeRateResponse;
import com.project.flowfinserver.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/exchange-rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateResponse>>> getExchangeRates() {
        List<ExchangeRateResponse> rates = exchangeRateService.findAllLatestRates();
        return ResponseEntity.ok(ApiResponse.success(rates, "현재 적용 환율 조회 완료"));
    }
}
