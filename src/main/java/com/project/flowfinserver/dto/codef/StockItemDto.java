package com.project.flowfinserver.dto.codef;

import java.math.BigDecimal;

public record StockItemDto(
        String productType,
        String itemName,
        String itemCode,
        Integer quantity,
        Long purchaseAmount,
        Long valuationAmt,
        Long valuationPl,
        BigDecimal earningsRate
) {}
