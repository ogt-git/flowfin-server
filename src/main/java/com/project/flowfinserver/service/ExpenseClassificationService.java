package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.MerchantCategoryRule;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Rule-based 지출 분류 서비스.
 * Rule 매칭 성공 → classifiedBy=RULE, confidence=rule.priority (EXACT=100, CONTAINS=70~95)
 * Rule 매칭 실패 → ClassificationResult.pending() 반환 (비동기 AI 분류 큐 투입 대상)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseClassificationService {

    private final ExpenseKeywordClassifier keywordClassifier;
    private final CategoryRepository categoryRepository;

    public ClassificationResult classify(String merchantName) {
        MerchantCategoryRule rule = keywordClassifier.classify(merchantName);
        if (rule != null) {
            Category category = categoryRepository.findById(rule.getCategoryId())
                    .orElseGet(() -> categoryRepository.findByName("기타지출").orElseThrow());
            log.debug("[Classification] Rule 분류 성공 merchantName={} categoryId={} confidence={}",
                    merchantName, rule.getCategoryId(), rule.getPriority());
            return ClassificationResult.ofRule(category, rule.getPriority());
        }

        log.debug("[Classification] Rule 분류 실패 → PENDING merchantName={}", merchantName);
        return ClassificationResult.pending();
    }
}
