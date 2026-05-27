package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;



public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndExpenseDateBetween(Long userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
            Long userId, LocalDateTime expenseDate, String merchantName, Long amount);

    List<Expense> findByUserIdAndCategoryIsNullOrderByExpenseDateDesc(Long userId);

    // 목록 조회: 월·카테고리 필터 + 페이지네이션
    // LEFT JOIN FETCH로 category 즉시 로딩 — countQuery는 JOIN FETCH 불필요
    @Query(value = "SELECT e FROM Expense e " +
            "LEFT JOIN FETCH e.category " +
            "WHERE e.userId = :userId " +
            "AND e.isExcluded = false " +
            "AND e.expenseDate BETWEEN :start AND :end " +
            "AND (:categoryId IS NULL OR e.category.id = :categoryId) " +
            "AND (:categoryType IS NULL OR e.category.type = :categoryType) " +
            "ORDER BY e.expenseDate DESC",
            countQuery = "SELECT COUNT(e) FROM Expense e " +
                    "WHERE e.userId = :userId " +
                    "AND e.isExcluded = false " +
                    "AND e.expenseDate BETWEEN :start AND :end " +
                    "AND (:categoryId IS NULL OR e.category.id = :categoryId) " +
                    "AND (:categoryType IS NULL OR e.category.type = :categoryType)")
    Page<Expense> findExpenses(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("categoryId") Long categoryId,
            @Param("categoryType") CategoryType categoryType,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
            "WHERE e.userId = :userId " +
            "AND e.isExcluded = false " +
            "AND e.expenseDate BETWEEN :start AND :end " +
            "AND (:categoryId IS NULL OR e.category.id = :categoryId) " +
            "AND (:categoryType IS NULL OR e.category.type = :categoryType)")
    Long sumAmounts(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("categoryId") Long categoryId,
            @Param("categoryType") CategoryType categoryType);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    // AssetService.computeFixedMonthlyAvg — category.id IN (:ids) AND expenseDate > :after
    List<Expense> findByUserIdAndCategoryIdInAndExpenseDateAfter(Long userId, List<Long> categoryIds, LocalDateTime after);

    // 제외 처리된 지출을 빼고 조회 — computeFixedCost·buildSpendingSummary 전용
    List<Expense> findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(Long userId, List<Long> categoryIds, LocalDateTime after);

    // "검토 필요" 배너: confidence < 60, 사용자 미수정, 미제외 건수
    @Query("SELECT COUNT(e) FROM Expense e " +
            "WHERE e.userId = :userId " +
            "AND e.categoryConfidence < 60 " +
            "AND e.isUserModified = false " +
            "AND e.isExcluded = false")
    long countLowConfidenceExpenses(@Param("userId") Long userId);

    // "신규 지출 N건" 배너: 당일 00:00 이후 생성, 사용자 미수정, 미제외 건수
    @Query("SELECT COUNT(e) FROM Expense e " +
            "WHERE e.userId = :userId " +
            "AND e.createdAt >= :startOfDay " +
            "AND e.isUserModified = false " +
            "AND e.isExcluded = false")
    long countNewExpensesSince(@Param("userId") Long userId,
                               @Param("startOfDay") LocalDateTime startOfDay);

    void deleteAllByUserId(Long userId);
}
