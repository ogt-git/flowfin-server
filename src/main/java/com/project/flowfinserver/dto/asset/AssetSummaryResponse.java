package com.project.flowfinserver.dto.asset;

public record AssetSummaryResponse(
        Long totalStockAsset,      // 증권 총 평가금액 합산
        Long depositReceived,      // 증권 예수금 합산
        Long liquidManualAsset,    // 유동 수동자산 합산 (DEPOSIT + SAVINGS + CASH)
        Long totalManualAsset,     // 전체 수동자산 합산
        Long investableAmount,     // 투자 가능 금액
        Long fixedMonthlyAvg,      // 고정비 월 평균 (최근 3개월)
        Long emergencyFund         // 비상금 (고정비 1개월치 기본)
) {}
