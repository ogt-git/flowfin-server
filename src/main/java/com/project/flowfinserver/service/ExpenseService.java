package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.expense.ExpenseResponse;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseStatsCacheManager expenseStatsCacheManager;

    @Transactional(readOnly = true)
    public ExpenseResponse getDetail(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new EntityNotFoundException("지출 내역을 찾을 수 없습니다."));
        return ExpenseResponse.from(expense, expense.getCategory());
    }

    @Transactional
    public ExpenseResponse updateCategory(Long expenseId, Long userId, Long categoryId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("지출 내역을 찾을 수 없습니다."));

        if (!expense.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인의 지출만 수정할 수 있습니다.");
        }

        if (expense.isExcluded()) {
            throw new IllegalStateException("제외 처리된 지출은 카테고리를 변경할 수 없습니다.");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));

        expense.updateCategoryByUser(category);

        if (expense.getExpenseDate() != null) {
            expenseStatsCacheManager.evict(userId, expense.getExpenseDate().format(MONTH_FMT));
        }

        return ExpenseResponse.from(expense, category);
    }

    @Transactional
    public void exclude(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("지출 내역을 찾을 수 없습니다."));

        if (!expense.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인의 지출만 제외 처리할 수 있습니다.");
        }

        if (expense.isExcluded()) {
            throw new IllegalStateException("이미 제외 처리된 지출입니다.");
        }

        expense.exclude();

        if (expense.getExpenseDate() != null) {
            expenseStatsCacheManager.evict(userId, expense.getExpenseDate().format(MONTH_FMT));
        }
    }

    @Transactional(readOnly = true)
    public int getLowConfidenceCount(Long userId) {
        return (int) expenseRepository.countLowConfidenceExpenses(userId);
    }

    @Transactional(readOnly = true)
    public int getNewExpenseCount(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return (int) expenseRepository.countNewExpensesSince(userId, startOfDay);
    }
}
