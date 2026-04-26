package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 10)
    private String icon;

    @Column(length = 20)
    private String color;

    @Column(name = "parent_id")
    private Long parentId; // null = 루트 카테고리, non-null = 하위 카테고리

    @Column(name = "is_fixed")
    private boolean isFixed;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_expense_type", length = 10)
    private ExpenseType defaultExpenseType = ExpenseType.VARIABLE;

    @Column(name = "sort_order")
    private int sortOrder;

    @Builder
    public Category(String name, String icon, String color,
                    Long parentId, boolean isFixed, ExpenseType defaultExpenseType, int sortOrder) {
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.parentId = parentId;
        this.isFixed = isFixed;
        this.defaultExpenseType = defaultExpenseType != null ? defaultExpenseType : ExpenseType.VARIABLE;
        this.sortOrder = sortOrder;
    }
}
