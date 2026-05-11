package com.project.flowfinserver.dto.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseStatsResponse {
    private int year;
    private int month;
    private long totalAmount;
    private long fixedAmount;
    private long variableAmount;
    private long irregularAmount;
    private List<CategoryStatDto> categoryStats;
}
