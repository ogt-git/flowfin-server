package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseClassificationService classificationService;

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
                result.getCategoryId(),
                result.getClassifiedBy(),
                result.getConfidence()
        ));

        log.debug("[Expense] 저장 userId={} merchant={} amount={} category={}",
                userId, dto.merchantName(), dto.amount(), result.getCategoryId());
        return true;
    }

    /**
     * CODEF 수신 거래를 분류 없이 PENDING 상태로 저장한다.
     * 중복 거래는 skip하며, DataIntegrityViolationException은 배치 연속성을 위해 전파하지 않는다.
     */
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

        try {
            expenseRepository.save(expense);
        } catch (DataIntegrityViolationException e) {
            log.warn("[Expense] 중복 저장 시도 감지 userId={} merchant={}", userId, dto.merchantName());
        }
    }
}
