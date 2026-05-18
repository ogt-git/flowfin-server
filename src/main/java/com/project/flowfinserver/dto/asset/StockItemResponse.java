package com.project.flowfinserver.dto.asset;

import java.math.BigDecimal;

public record StockItemResponse(
        String itemName,
        String itemCode,
        Integer quantity,
        Long valuationAmt,
        Long valuationPl,
        BigDecimal earningsRate
) {}
