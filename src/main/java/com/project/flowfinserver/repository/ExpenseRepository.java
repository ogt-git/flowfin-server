package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndExpenseDateBetween(Long userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
            Long userId, LocalDateTime expenseDate, String merchantName, Long amount);

    Page<Expense> findAllByUserIdAndIsExcludedFalse(Long userId, Pageable pageable);

    List<Expense> findByUserIdAndCategoryIdIsNullOrderByExpenseDateDesc(Long userId);

    List<Expense> findByUserIdAndCategoryIdInAndExpenseDateAfter(
            Long userId, List<Long> categoryIds, LocalDateTime after);
}
