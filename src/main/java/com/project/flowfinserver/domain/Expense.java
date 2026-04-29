package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;


@Entity
@Table(name = "expense",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_expense",
                columnNames = {"user_id", "transacted_at", "merchant_name", "amount"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transacted_at", nullable = false)
    private LocalDate transactedAt;

    @Column(name = "merchant_name", nullable = false, length = 200)
    private String merchantName;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "card_company", length = 50)
    private String cardCompany;

    @Column(name = "category_id")
    private Long categoryId; // FK to category.id — null이면 미분류

    @Enumerated(EnumType.STRING)
    @Column(name = "classified_by", length = 10)
    private ClassifiedBy classifiedBy; // 분류 주체 (RULE/AI/USER)

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", length = 10)
    private ExpenseType expenseType = ExpenseType.VARIABLE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "JSON")
    private Map<String, Object> rawData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Expense(Long userId, LocalDate transactedAt, String merchantName,
                   Long amount, String cardCompany, Long categoryId,
                   ClassifiedBy classifiedBy, ExpenseType expenseType, Map<String, Object> rawData) {
        this.userId = userId;
        this.transactedAt = transactedAt;
        this.merchantName = merchantName;
        this.amount = amount;
        this.cardCompany = cardCompany;
        this.categoryId = categoryId;
        this.classifiedBy = classifiedBy;
        this.expenseType = expenseType != null ? expenseType : ExpenseType.VARIABLE;
        this.rawData = rawData;
    }

    public void updateCategory(Long categoryId, ClassifiedBy classifiedBy) {
        this.categoryId = categoryId;
        this.classifiedBy = classifiedBy;
    }
}
