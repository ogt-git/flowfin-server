package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.constant.CategoryMetaConstants;
import com.project.flowfinserver.constant.CategoryMetaConstants.CategoryMeta;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.expense.CategoryStatDto;
import com.project.flowfinserver.dto.expense.MonthlyStatsResponse;
import com.project.flowfinserver.repository.ExpenseStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseStatsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseStatsRepository expenseStatsRepository;
    private final ExpenseStatsCacheManager cacheManager;

    public MonthlyStatsResponse getMonthlyStats(Long userId, String month) {

        // 1. PENDING 건 존재 여부 — 분류 중이면 캐시 조회 스킵 (불완전한 결과를 캐싱하면 안 됨)
        boolean classifying = expenseStatsRepository
                .countPendingByUserIdAndMonth(userId, month, ClassifiedBy.PENDING) > 0;

        // 2. Redis 캐시 조회 (분류 완료 상태에서만)
        if (!classifying) {
            MonthlyStatsResponse cached = cacheManager.get(userId, month);
            if (cached != null) return cached;
        }

        // 3. 카테고리별 집계 (지출 있는 카테고리만 — amount=0 건은 아래에서 채움)
        List<Object[]> rows = expenseStatsRepository.findCategoryStatsByUserIdAndMonth(userId, month);
        Map<Long, Long> amountByCategory = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            long catId = ((Number) row[0]).longValue();
            long amt   = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            amountByCategory.put(catId, amt);
        }

        // 4. 당월 합계
        Long totalRaw = expenseStatsRepository.findTotalAmountByUserIdAndMonth(userId, month);
        long totalAmount = totalRaw != null ? totalRaw : 0L;

        // 5. 11개 카테고리 전부 포함한 CategoryStatDto 구성 (amount=0 카테고리도 포함)
        long fixedAmount = 0L, variableAmount = 0L, etcAmount = 0L;
        List<CategoryStatDto> categoryStats = new ArrayList<>();

        for (CategoryMeta meta : CategoryMetaConstants.META.values()) {
            long amount = amountByCategory.getOrDefault((long) meta.id(), 0L);
            double ratio = totalAmount > 0
                    ? Math.round((double) amount / totalAmount * 1000.0) / 10.0
                    : 0.0;

            categoryStats.add(new CategoryStatDto(
                    meta.id(), meta.name(), meta.icon(), meta.color(), amount, ratio));

            if (meta.type() == CategoryType.FIXED)         fixedAmount    += amount;
            else if (meta.type() == CategoryType.VARIABLE) variableAmount += amount;
            else                                            etcAmount      += amount;
        }

        // 6. amount DESC 정렬
        categoryStats.sort(Comparator.comparingLong(CategoryStatDto::amount).reversed());

        MonthlyStatsResponse response = new MonthlyStatsResponse(
                month, totalAmount, fixedAmount, variableAmount, etcAmount, categoryStats, classifying);

        // 7. 분류 완료 상태에서만 Redis 캐시 저장 (분류 중엔 캐싱 스킵)
        if (!classifying) {
            cacheManager.set(userId, month, response);
        }
        return response;
    }

    public String findLatestMonth(Long userId) {
        return expenseStatsRepository.findLatestExpenseMonth(userId);
    }

    // Expense INSERT 또는 is_excluded 변경 시 호출하여 캐시 무효화
    public void evictCache(Long userId, String month) {
        cacheManager.evict(userId, month);
    }
}
