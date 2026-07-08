package com.project.flowfinserver.dto.asset;

import java.math.BigDecimal;

public record StockItemResponse(
        String productType,
        String itemName,
        String itemCode,
        Integer quantity,
        Long purchaseAmount,
        Long valuationAmt,
        Long valuationPl,
        BigDecimal earningsRate
) {}
