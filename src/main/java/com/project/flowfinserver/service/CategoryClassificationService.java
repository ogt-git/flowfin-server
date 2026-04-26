package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.ExpenseType;
import com.project.flowfinserver.domain.MatchType;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.MerchantCategoryRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Rule-based 분류 1차 처리 — 미분류 항목만 AI(GPT-4) 호출로 비용 최소화
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryClassificationService {

    private final MerchantCategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 가맹점명으로 카테고리 ID 조회.
     * EXACT 매칭 우선, 없으면 CONTAINS 매칭, 둘 다 없으면 "기타지출" 카테고리 반환.
     */
    public Optional<Long> classifyByRule(String merchantName) {
        Optional<Long> exact = ruleRepository
                .findFirstByKeywordAndMatchTypeOrderByPriorityDesc(merchantName, MatchType.EXACT)
                .map(rule -> rule.getCategoryId());
        if (exact.isPresent()) return exact;

        Optional<Long> contains = ruleRepository
                .findFirstContainingKeyword(merchantName)
                .map(rule -> rule.getCategoryId());
        if (contains.isPresent()) return contains;

        return categoryRepository.findByName("기타지출").map(c -> c.getId());
    }

    public ClassifiedBy resolveClassifiedBy(Long categoryId) {
        return categoryId != null ? ClassifiedBy.RULE : null;
    }

    /** 카테고리의 default_expense_type을 조회해 expenseType 결정 */
    public ExpenseType resolveExpenseType(Long categoryId) {
        if (categoryId == null) return ExpenseType.VARIABLE;
        return categoryRepository.findById(categoryId)
                .map(c -> c.getDefaultExpenseType())
                .orElse(ExpenseType.VARIABLE);
    }
}
