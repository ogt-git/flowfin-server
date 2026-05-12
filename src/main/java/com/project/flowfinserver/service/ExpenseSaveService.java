package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private final ExpenseRepository expenseRepository;
    private final ExpenseClassificationService classificationService;

    @Transactional
    public int saveExpenses(Long userId, List<CardBillingDto> items) {
        int savedCount = 0;

        for (CardBillingDto item : items) {
            ClassificationResult result = classificationService.classify(item.merchantName(), item.amount());

            Expense expense = Expense.create(
                    userId,
                    item.cardCompany(),
                    item.amount(),
                    item.merchantName(),
                    item.expenseDate(),
                    result.getCategoryId(),
                    result.getClassifiedBy(),
                    result.getConfidence()
            );

            try {
                expenseRepository.save(expense);
                savedCount++;
                log.debug("[ExpenseSave] 저장 userId={} merchant={} amount={} category={}",
                        userId, item.merchantName(), item.amount(), result.getCategoryId());
            } catch (DataIntegrityViolationException e) {
                log.debug("[ExpenseSave] 중복 스킵 userId={} merchant={} date={} amount={}",
                        userId, item.merchantName(), item.expenseDate(), item.amount());
            }
        }

        log.info("[ExpenseSave] 완료 userId={} 저장={}건 / 전체={}건", userId, savedCount, items.size());
        return savedCount;
    }
}
