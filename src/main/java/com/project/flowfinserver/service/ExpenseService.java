package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.dto.expense.ExpenseResponse;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository expenseRepository;
    private final ExpenseClassificationService classificationService;
    private final CategoryRepository categoryRepository;
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
        // 단기카드대출(4) / 장기카드대출(5)은 개인 지출이 아니므로 제외 처리
        if ("4".equals(dto.paymentType()) || "5".equals(dto.paymentType())) {
            expense.exclude();
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
    }
}
