package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Category; // CHANGED
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.repository.CategoryRepository; // CHANGED
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Rule-based → AI(GPT-4) 하이브리드 지출 분류 파이프라인.
 *
 * 1단계: ExpenseKeywordClassifier.classify() (in-memory, O(1)/O(n))
 * 2단계: 1단계 실패 시 GptClassificationService.classify() 위임
 *        (현재는 Stub, 6주차에 실제 GPT-4 구현으로 교체)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseClassificationService {

    private final ExpenseKeywordClassifier keywordClassifier;
    private final GptClassificationService gptClassificationService;
    private final CategoryRepository categoryRepository; // CHANGED

    public ClassificationResult classify(String merchantName, Long amount) {
        Long categoryId = keywordClassifier.classify(merchantName);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId) // CHANGED
                    .orElseGet(() -> categoryRepository.findByName("기타지출").orElseThrow()); // CHANGED
            log.debug("[Classification] Rule 분류 성공 merchantName={} categoryId={}", merchantName, categoryId);
            return ClassificationResult.ofRule(category); // CHANGED
        }

        log.debug("[Classification] Rule 분류 실패 → GPT 위임 merchantName={}", merchantName);
        return gptClassificationService.classify(merchantName, amount);
    }
}
