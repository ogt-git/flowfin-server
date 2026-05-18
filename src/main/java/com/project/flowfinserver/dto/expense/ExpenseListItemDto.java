package com.project.flowfinserver.dto.expense;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.Expense;

public record ExpenseListItemDto(
        Long expenseId,
        String cardCompany,
        Long amount,
        String merchantName,
        String expenseDate,
        Long categoryId,
        String categoryName,
        String categoryType,
        String classifiedBy,
        boolean isUserModified
) {
    public static ExpenseListItemDto from(Expense expense, Category category) {
        return new ExpenseListItemDto(
                expense.getId(),
                expense.getCardCompany(),
                expense.getAmount(),
                expense.getMerchantName(),
                expense.getExpenseDate() != null ? expense.getExpenseDate().toString() : null,
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getType().name() : null,
                expense.getClassifiedBy() != null ? expense.getClassifiedBy().name() : null,
                expense.isUserModified()
        );
    }
}
