package com.project.flowfinserver.dto.asset;

import java.util.List;

public record StockAccountResponse(
        String brokerCode,
        String accountNo,
        Long totalAsset,
        Long depositReceived,
        List<StockItemResponse> items
) {}
