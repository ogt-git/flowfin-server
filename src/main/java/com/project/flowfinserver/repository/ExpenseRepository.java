package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndTransactedAtBetween(Long userId, LocalDate from, LocalDate to);

    boolean existsByUserIdAndTransactedAtAndMerchantNameAndAmount(
            Long userId, LocalDate transactedAt, String merchantName, Long amount);

    List<Expense> findByUserIdAndCategoryIdIsNullOrderByTransactedAtDesc(Long userId);
}
