package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 통계 전용 읽기 쿼리 — Repository<Expense, Long> 확장으로 CRUD 노출 차단
// 기존 ExpenseRepository와 동일 엔티티를 다루지만 JPA 관리 충돌 없음
public interface ExpenseStatsRepository extends Repository<Expense, Long> {

    // 카테고리별 지출 합계 — 지출이 있는 카테고리만 반환 (amount=0 카테고리는 서비스에서 채움)
    // 반환: Object[]{ categoryId(Long), totalAmount(Long) }
    // JOIN e.category c → PENDING(category=null) 건 자동 제외
    // 취소·환불(음수)도 포함하여 카테고리별 순지출 반영
    @Query("SELECT c.id, SUM(e.amount) " +
           "FROM Expense e " +
           "JOIN e.category c " +
           "WHERE e.userId = :userId " +
           "AND e.isExcluded = false " +
           "AND FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m') = :month " +
           "GROUP BY c.id")
    List<Object[]> findCategoryStatsByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("month") String month);

    // 월 합계 — 당월·전월 모두 이 메서드 재사용
    // COALESCE로 데이터 없을 때 0 반환 보장
    // 순지출 의도 — amount 필터 의도적 미적용: 취소·환불(음수)이 총액에서 차감되어야 정확한 순지출이 됨
    @Query("SELECT COALESCE(SUM(e.amount), 0) " +
           "FROM Expense e " +
           "WHERE e.userId = :userId " +
           "AND e.isExcluded = false " +
           "AND FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m') = :month")
    Long findTotalAmountByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("month") String month);

    // 데이터가 있는 가장 최근 월 반환 (없으면 null)
    @Query("SELECT FUNCTION('DATE_FORMAT', MAX(e.expenseDate), '%Y-%m') " +
           "FROM Expense e " +
           "WHERE e.userId = :userId " +
           "AND e.isExcluded = false")
    String findLatestExpenseMonth(@Param("userId") Long userId);

    // AI 분류 대기 건수 — 0보다 크면 stats가 아직 incomplete 상태
    @Query("SELECT COUNT(e) " +
           "FROM Expense e " +
           "WHERE e.userId = :userId " +
           "AND e.isExcluded = false " +
           "AND e.classifiedBy = :pending " +
           "AND FUNCTION('DATE_FORMAT', e.expenseDate, '%Y-%m') = :month")
    long countPendingByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("month") String month,
            @Param("pending") ClassifiedBy pending);
}
