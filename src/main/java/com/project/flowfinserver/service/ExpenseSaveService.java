package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CODEF 카드 청구 내역을 분류하여 Expense 테이블에 저장한다.
 *
 * 규칙:
 * - is_user_modified=true 인 Expense는 절대 카테고리 덮어쓰기 금지 (Expense.updateCategory 내부 보호)
 * - Expense 물리 삭제 금지 (is_excluded=true 소프트 처리만 허용)
 * - UNIQUE 제약(user_id, expense_date, merchant_name, amount) 위반 시 중복으로 간주하고 skip
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseSaveService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository expenseRepository;
    private final ExpenseClassificationService classificationService;
    private final ExpenseStatsCacheManager expenseStatsCacheManager;

    @Transactional
    public int saveExpenses(Long userId, List<CardBillingDto> items) {
        int savedCount = 0;
        int excludedCount = 0;

        for (CardBillingDto item : items) {
            boolean duplicate = expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                    userId, item.expenseDate(), item.merchantName(), item.amount());
            if (duplicate) {
                log.debug("[ExpenseSave] 중복 스킵 userId={} merchant={} date={} amount={}",
                        userId, item.merchantName(), item.expenseDate(), item.amount());
                continue;
            }

            ClassificationResult result = classificationService.classify(item.merchantName(), item.amount());

            Expense expense = Expense.create(
                    userId,
                    item.cardCompany(),
                    item.amount(),
                    item.merchantName(),
                    item.expenseDate(),
                    result.getCategory(),
                    result.getClassifiedBy(),
                    result.getConfidence()
            );
            // 단기카드대출(4) / 장기카드대출(5) → is_excluded=true 소프트 처리
            // 취소 거래는 resUsedAmount 음수로 저장되어 통계에서 자동 상쇄됨
            if ("4".equals(item.paymentType()) || "5".equals(item.paymentType())) {
                expense.exclude();
                excludedCount++;
            }

            expenseRepository.save(expense);
            savedCount++;
            log.debug("[ExpenseSave] 저장 userId={} merchant={} amount={} category={} excluded={}",
                    userId, item.merchantName(), item.amount(), result.getCategory(), expense.isExcluded());
        }

        log.info("[ExpenseSave] 완료 userId={} 저장={}건 (제외포함) / 전체={}건 / 제외={}건",
                userId, savedCount, items.size(), excludedCount);

        // 저장된 지출의 월별 통계 캐시 무효화 (여러 달에 걸친 내역일 수 있으므로 월 단위로 수집)
        if (savedCount > 0) {
            Set<String> months = new HashSet<>();
            for (CardBillingDto item : items) {
                if (item.expenseDate() != null) {
                    months.add(item.expenseDate().format(MONTH_FMT));
                }
            }
            months.forEach(month -> expenseStatsCacheManager.evict(userId, month));
        }

        return savedCount;
    }
}
