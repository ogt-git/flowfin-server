package com.project.flowfinserver.dto.expense;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ExpenseItemResponse {
    private Long id;
    private LocalDate expenseDate;
    private String merchantName;
    private long amount;
    private String cardCompany;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String expenseType;
    private boolean excluded;
    private String classifiedBy;
}
