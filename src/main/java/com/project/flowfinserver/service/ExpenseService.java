package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.dto.expense.ExpenseResponse;
import com.project.flowfinserver.domain.ExpenseType;
import com.project.flowfinserver.dto.expense.CategoryStatDto;
import com.project.flowfinserver.dto.expense.ExpenseItemResponse;
import com.project.flowfinserver.dto.expense.ExpenseStatsResponse;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String STATS_CACHE_PREFIX = "expense:stats:";
    private static final long STATS_CACHE_TTL_SECONDS = 3600L;
    private final ExpenseRepository expenseRepository;
    private final ExpenseClassificationService classificationService;
    private final CategoryRepository categoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExpenseStatsCacheManager expenseStatsCacheManager;

    /**
     * 중복 체크 후 분류하여 지출 저장.
     * @return true = 저장됨, false = 중복 스킵
     */
    @Transactional
    public boolean saveIfNotDuplicate(Long userId, CardBillingDto dto) {
        if (expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                userId, dto.expenseDate(), dto.merchantName(), dto.amount())) {
            log.debug("[Expense] 중복 스킵 userId={} merchant={} date={} amount={}",
                    userId, dto.merchantName(), dto.expenseDate(), dto.amount());
            return false;
        }

        ClassificationResult result = classificationService.classify(dto.merchantName(), dto.amount());
        expenseRepository.save(Expense.create(
                userId,
                dto.cardCompany(),
                dto.amount(),
                dto.merchantName(),
                dto.expenseDate(),
                result.getCategory(), // CHANGED
                result.getClassifiedBy(),
                result.getConfidence()
        ));

        log.debug("[Expense] 저장 userId={} merchant={} amount={} category={}",
                userId, dto.merchantName(), dto.amount(), result.getCategory()); // CHANGED
        return true;
    }

    /**
     * GET /api/expenses/details/{id}
     * 지출 상세 조회 — 본인 지출만 접근 가능
     */
    @Transactional(readOnly = true)
    public ExpenseResponse getDetail(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new EntityNotFoundException("지출 내역을 찾을 수 없습니다."));
        return ExpenseResponse.from(expense, expense.getCategory());
    }

    /**
     * PUT /api/expenses/category/{id}
     * 사용자 수동 카테고리 수정 — classified_by=USER, is_user_modified=true, confidence=null 저장
     */
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

        expense.updateCategoryByUser(category); // CHANGED

        // 카테고리 변경 시 해당 월 통계 캐시 무효화
        if (expense.getExpenseDate() != null) {
            expenseStatsCacheManager.evict(userId, expense.getExpenseDate().format(MONTH_FMT));
        }

        return ExpenseResponse.from(expense, category);
    }

    /**
     * DELETE /api/expenses/delete/{id}
     * 지출 제외 처리 — is_excluded=true 소프트 처리만 허용, DB 물리 삭제 금지
     */
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

    /**
     * GET /api/expenses/review-count
     * confidence < 60, 사용자 미수정, 미제외 건수 반환
     */
    @Transactional(readOnly = true)
    public int getLowConfidenceCount(Long userId) {
        return (int) expenseRepository.countLowConfidenceExpenses(userId);
    }

    /**
     * GET /api/expenses/new-count
     * 오늘 00:00:00 이후 생성된 신규 지출 건수 반환
     */
    @Transactional(readOnly = true)
    public int getNewExpenseCount(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return (int) expenseRepository.countNewExpensesSince(userId, startOfDay);
    }
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
        long etcAmount = 0L;
        Map<Long, Long> categoryAmountMap = new LinkedHashMap<>();

        for (Expense e : expenses) {
            totalAmount += e.getAmount();
            ExpenseType type = e.getExpenseType();
            if (type == ExpenseType.FIXED)          fixedAmount += e.getAmount();
            else if (type == ExpenseType.VARIABLE)  variableAmount += e.getAmount();
            else if (type == ExpenseType.IRREGULAR) etcAmount += e.getAmount();

            if (e.getCategoryId() != null) {
                categoryAmountMap.merge(e.getCategoryId(), e.getAmount(), Long::sum);
            }
        }

        // 전월 대비 증감률 계산
        LocalDate prevMonth = LocalDate.of(year, month, 1).minusMonths(1);
        List<Expense> prevExpenses = expenseRepository.findMonthlyExpenses(
                userId, prevMonth.getYear(), prevMonth.getMonthValue());
        long prevTotal = prevExpenses.stream().mapToLong(Expense::getAmount).sum();
        Double changePercent = prevTotal > 0
                ? Math.round((totalAmount - prevTotal) * 1000.0 / prevTotal) / 10.0
                : null;

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
                .etcAmount(etcAmount)
                .changePercent(changePercent)
                .categoryStats(categoryStats)
                .build();
        @Transactional
        public void saveFromCodef(Long userId, CardBillingDto dto) {
            if (expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                    userId, dto.expenseDate(), dto.merchantName(), dto.amount())) {
                log.debug("[Expense] 중복 거래 skip userId={} merchant={}", userId, dto.merchantName());
                return;
            }

            Expense expense = Expense.create(
                    userId,
                    dto.cardCompany(),
                    dto.amount(),
                    dto.merchantName(),
                    dto.expenseDate(),
                    null,
                    ClassifiedBy.PENDING,
                    null
            );
            // 단기카드대출(4) / 장기카드대출(5)은 개인 지출이 아니므로 제외 처리
            if ("4".equals(dto.paymentType()) || "5".equals(dto.paymentType())) {
                expense.exclude();
            }
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
            try {
                expenseRepository.save(expense);
                // 신규 지출 저장 시 해당 월 통계 캐시 무효화
                if (dto.expenseDate() != null) {
                    expenseStatsCacheManager.evict(userId, dto.expenseDate().format(MONTH_FMT));
                }
            } catch (DataIntegrityViolationException e) {
                log.warn("[Expense] 중복 저장 시도 감지 userId={} merchant={}", userId, dto.merchantName());
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
