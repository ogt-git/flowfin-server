package com.project.flowfinserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

    private final ExchangeRateService exchangeRateService;

    /**
     * 한국수출입은행 일환율 데이터는 영업일 11시 전후 업데이트됨.
     * 평일 11:10에 조회하여 오늘 데이터가 없으면 최대 7일 전까지 fallback.
     */
    @Scheduled(cron = "0 10 11 * * MON-FRI", zone = "Asia/Seoul")
    public void refreshExchangeRates() {
        log.info("[ExchangeRate] 환율 갱신 스케줄 시작");
        try {
            exchangeRateService.fetchWithFallback();
        } catch (Exception e) {
            log.error("[ExchangeRate] 환율 갱신 스케줄 중 예외 발생 — 기존 값 유지 error={}", e.getMessage());
        }
    }
}
