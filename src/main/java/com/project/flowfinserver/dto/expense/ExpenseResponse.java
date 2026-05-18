package com.project.flowfinserver.dto.expense;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.Expense;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpenseResponse {

    private Long id;
    private String merchantName;
    private Long amount;
    private String expenseDate;   // ISO 형식
    private String cardCompany;
    private boolean isExcluded;

    private String classifiedBy;        // "RULE" | "AI" | "USER" | "PENDING"
    private Integer categoryConfidence; // USER 수정 시 null
    private boolean isUserModified;

    private Long categoryId;
    private String categoryName;
    private String categoryType;        // "FIXED" | "VARIABLE" | "ETC"

    public static ExpenseResponse from(Expense expense, Category category) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .merchantName(expense.getMerchantName())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate() != null ? expense.getExpenseDate().toString() : null)
                .cardCompany(expense.getCardCompany())
                .isExcluded(expense.isExcluded())
                .classifiedBy(expense.getClassifiedBy() != null ? expense.getClassifiedBy().name() : null)
                .categoryConfidence(expense.getCategoryConfidence())
                .isUserModified(expense.isUserModified())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryType(category != null ? category.getType().name() : null)
                .build();
    }
}
