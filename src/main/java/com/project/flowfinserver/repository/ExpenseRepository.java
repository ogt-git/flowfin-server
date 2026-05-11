package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndExpenseDateBetween(Long userId, LocalDate from, LocalDate to);

    boolean existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
            Long userId, LocalDate expenseDate, String merchantName, Long amount);

    List<Expense> findByUserIdAndCategoryIdIsNullOrderByExpenseDateDesc(Long userId);

    // 지출 목록 조회 (필터 + 페이지네이션, 제외 항목 제외)
    Page<Expense> findByUserIdAndIsExcludedFalseOrderByExpenseDateDesc(Long userId, Pageable pageable);

    // 기간 + 카테고리 필터
    Page<Expense> findByUserIdAndExpenseDateBetweenAndIsExcludedFalseOrderByExpenseDateDesc(
            Long userId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Expense> findByUserIdAndCategoryIdAndExpenseDateBetweenAndIsExcludedFalseOrderByExpenseDateDesc(
            Long userId, Long categoryId, LocalDate from, LocalDate to, Pageable pageable);

    // 월별 지출 통계용
    @Query("SELECT e FROM Expense e WHERE e.userId = :userId " +
           "AND YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month " +
           "AND e.isExcluded = false")
    List<Expense> findMonthlyExpenses(@Param("userId") Long userId,
                                      @Param("year") int year,
                                      @Param("month") int month);
}
