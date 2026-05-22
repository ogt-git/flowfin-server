package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Rule-based 지출 분류 서비스.
 * Rule 매칭 성공 → classifiedBy=RULE, confidence=100
 * Rule 매칭 실패 → ClassificationResult.pending() 반환 (비동기 AI 분류 큐 투입 대상)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseClassificationService {

    private final ExpenseKeywordClassifier keywordClassifier;
    private final CategoryRepository categoryRepository;

    public ClassificationResult classify(String merchantName) {
        Long categoryId = keywordClassifier.classify(merchantName);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseGet(() -> categoryRepository.findByName("기타지출").orElseThrow());
            log.debug("[Classification] Rule 분류 성공 merchantName={} categoryId={}", merchantName, categoryId);
            return ClassificationResult.ofRule(category);
        }

        log.debug("[Classification] Rule 분류 실패 → PENDING merchantName={}", merchantName);
        return ClassificationResult.pending();
    }
}
