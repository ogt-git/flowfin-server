package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.ExpenseSaveResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CODEF 카드 청구 내역을 Rule 분류 후 Expense 테이블에 저장한다.
 * Rule 실패 Expense는 classifiedBy=PENDING 으로 저장되며,
 * 호출자가 트랜잭션 커밋 후 AiExpenseClassifier로 비동기 분류를 트리거해야 한다.
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
    public ExpenseSaveResult saveExpenses(Long userId, List<CardBillingDto> items) {
        int savedCount = 0;
        int excludedCount = 0;
        List<Long> pendingAiIds = new ArrayList<>();

        for (CardBillingDto item : items) {
            boolean duplicate = expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                    userId, item.expenseDate(), item.merchantName(), item.amount());
            if (duplicate) {
                log.debug("[ExpenseSave] 중복 스킵 userId={} merchant={} date={} amount={}",
                        userId, item.merchantName(), item.expenseDate(), item.amount());
                continue;
            }

            ClassificationResult result = classificationService.classify(item.merchantName());

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
            if ("4".equals(item.paymentType()) || "5".equals(item.paymentType())) {
                expense.exclude();
                excludedCount++;
            }

            Expense saved = expenseRepository.save(expense);
            savedCount++;

            if (result.getClassifiedBy() == ClassifiedBy.PENDING) {
                pendingAiIds.add(saved.getId());
            }

            log.debug("[ExpenseSave] 저장 userId={} merchant={} amount={} classifiedBy={} excluded={}",
                    userId, item.merchantName(), item.amount(), result.getClassifiedBy(), expense.isExcluded());
        }

        log.info("[ExpenseSave] 완료 userId={} 저장={}건 (제외포함) / 전체={}건 / 제외={}건 / AI대기={}건",
                userId, savedCount, items.size(), excludedCount, pendingAiIds.size());

        if (savedCount > 0) {
            Set<String> months = new HashSet<>();
            for (CardBillingDto item : items) {
                if (item.expenseDate() != null) {
                    months.add(item.expenseDate().format(MONTH_FMT));
                }
            }
            months.forEach(month -> expenseStatsCacheManager.evict(userId, month));
        }

        return new ExpenseSaveResult(savedCount, pendingAiIds);
    }
}
