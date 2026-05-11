package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.domain.ExpenseType;
import com.project.flowfinserver.dto.expense.CategoryStatDto;
import com.project.flowfinserver.dto.expense.ExpenseItemResponse;
import com.project.flowfinserver.dto.expense.ExpenseStatsResponse;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private static final String STATS_CACHE_PREFIX = "expense:stats:";
    private static final long STATS_CACHE_TTL_SECONDS = 3600L;

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ExpenseStatsResponse getMonthlyStats(Long userId, int year, int month) {
        String cacheKey = STATS_CACHE_PREFIX + userId + ":" + String.format("%d%02d", year, month);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ExpenseStatsResponse.class);
            } catch (Exception e) {
                log.warn("stats cache parse error for key={}", cacheKey, e);
            }
        }

        List<Expense> expenses = expenseRepository.findMonthlyExpenses(userId, year, month);
        Map<Long, Category> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        long totalAmount = 0L;
        long fixedAmount = 0L;
        long variableAmount = 0L;
        long irregularAmount = 0L;
        Map<Long, Long> categoryAmountMap = new LinkedHashMap<>();

        for (Expense e : expenses) {
            totalAmount += e.getAmount();
            ExpenseType type = e.getExpenseType();
            if (type == ExpenseType.FIXED)          fixedAmount += e.getAmount();
            else if (type == ExpenseType.VARIABLE)  variableAmount += e.getAmount();
            else if (type == ExpenseType.IRREGULAR) irregularAmount += e.getAmount();

            if (e.getCategoryId() != null) {
                categoryAmountMap.merge(e.getCategoryId(), e.getAmount(), Long::sum);
            }
        }

        final long total = totalAmount;
        List<CategoryStatDto> categoryStats = categoryAmountMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> {
                    Category cat = categoryMap.get(entry.getKey());
                    double ratio = total > 0
                            ? Math.round(entry.getValue() * 1000.0 / total) / 10.0
                            : 0.0;
                    return CategoryStatDto.builder()
                            .categoryId(entry.getKey())
                            .name(cat != null ? cat.getName() : "기타")
                            .icon(cat != null ? cat.getIcon() : null)
                            .color(cat != null ? cat.getColor() : null)
                            .amount(entry.getValue())
                            .ratio(ratio)
                            .build();
                })
                .collect(Collectors.toList());

        ExpenseStatsResponse response = ExpenseStatsResponse.builder()
                .year(year)
                .month(month)
                .totalAmount(totalAmount)
                .fixedAmount(fixedAmount)
                .variableAmount(variableAmount)
                .irregularAmount(irregularAmount)
                .categoryStats(categoryStats)
                .build();

        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, STATS_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("stats cache write error for key={}", cacheKey, e);
        }

        return response;
    }

    public Page<ExpenseItemResponse> getExpenseList(Long userId, LocalDate from, LocalDate to,
                                                    Long categoryId, Pageable pageable) {
        Map<Long, Category> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        Page<Expense> expensePage;
        if (from != null && to != null && categoryId != null) {
            expensePage = expenseRepository
                    .findByUserIdAndCategoryIdAndExpenseDateBetweenAndIsExcludedFalseOrderByExpenseDateDesc(
                            userId, categoryId, from, to, pageable);
        } else if (from != null && to != null) {
            expensePage = expenseRepository
                    .findByUserIdAndExpenseDateBetweenAndIsExcludedFalseOrderByExpenseDateDesc(
                            userId, from, to, pageable);
        } else {
            expensePage = expenseRepository
                    .findByUserIdAndIsExcludedFalseOrderByExpenseDateDesc(userId, pageable);
        }

        return expensePage.map(e -> toItemResponse(e, categoryMap));
    }

    private ExpenseItemResponse toItemResponse(Expense e, Map<Long, Category> categoryMap) {
        Category cat = e.getCategoryId() != null ? categoryMap.get(e.getCategoryId()) : null;
        return ExpenseItemResponse.builder()
                .id(e.getId())
                .expenseDate(e.getExpenseDate())
                .merchantName(e.getMerchantName())
                .amount(e.getAmount())
                .cardCompany(e.getCardCompany())
                .categoryId(e.getCategoryId())
                .categoryName(cat != null ? cat.getName() : null)
                .categoryIcon(cat != null ? cat.getIcon() : null)
                .expenseType(e.getExpenseType() != null ? e.getExpenseType().name() : null)
                .excluded(e.isExcluded())
                .classifiedBy(e.getClassifiedBy() != null ? e.getClassifiedBy().name() : null)
                .build();
    }
}
