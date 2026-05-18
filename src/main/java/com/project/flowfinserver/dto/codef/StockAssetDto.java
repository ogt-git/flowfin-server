package com.project.flowfinserver.dto.codef;

public record StockAssetDto(
        String brokerCode,
        String accountNo,
        Long totalAsset,
        Long depositReceived
) {}
