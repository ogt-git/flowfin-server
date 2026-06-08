package com.project.flowfinserver.dto.expense;

import java.util.List;

public record MonthlyStatsResponse(
        String month,
        long totalAmount,
        long fixedAmount,
        long variableAmount,
        long etcAmount,
        List<CategoryStatDto> categoryStats,
        boolean classifying
) {}
