package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "expense",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_expense",
                columnNames = {"user_id", "expense_date", "merchant_name", "amount"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_company", length = 50)
    private String cardCompany;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "merchant_name", nullable = false, length = 255)
    private String merchantName;

    @Column(name = "expense_date", nullable = false)
    private LocalDateTime expenseDate;

    @ManyToOne(fetch = FetchType.LAZY) // CHANGED
    @JoinColumn(name = "category_id")  // CHANGED
    private Category category;         // CHANGED

    @Enumerated(EnumType.STRING)
    @Column(name = "classified_by", length = 10)
    private ClassifiedBy classifiedBy;

    @Column(name = "category_confidence")
    private Integer categoryConfidence;

    @Column(name = "is_user_modified", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isUserModified;

    @Column(name = "is_excluded", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isExcluded;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Expense create(Long userId, String cardCompany, Long amount,
                                  String merchantName, LocalDateTime expenseDate,
                                  Category category, ClassifiedBy classifiedBy, // CHANGED
                                  Integer categoryConfidence) {
        Expense expense = new Expense();
        expense.userId = userId;
        expense.cardCompany = cardCompany;
        expense.amount = amount;
        expense.merchantName = merchantName;
        expense.expenseDate = expenseDate;
        expense.category = category; // CHANGED
        expense.classifiedBy = classifiedBy;
        expense.categoryConfidence = categoryConfidence;
        expense.isUserModified = false;
        expense.isExcluded = false;
        return expense;
    }

    // AI/Rule 재분류 — is_user_modified=true 이면 변경 불가
    public void updateCategory(Category category, ClassifiedBy classifiedBy, Integer categoryConfidence) { // CHANGED
        if (this.isUserModified) return;
        this.category = category; // CHANGED
        this.classifiedBy = classifiedBy;
        this.categoryConfidence = categoryConfidence;
    }

    // 사용자 수동 수정 — 이후 자동 분류로 덮어쓰기 불가
    public void updateCategoryByUser(Category category) { // CHANGED
        this.category = category; // CHANGED
        this.classifiedBy = ClassifiedBy.USER;
        this.categoryConfidence = null;
        this.isUserModified = true;
    }

    public void exclude() {
        this.isExcluded = true;
    }
}
