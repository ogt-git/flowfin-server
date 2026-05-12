package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.ClassifiedBy;
import lombok.Getter;

@Getter
public class ClassificationResult {

    private final Long categoryId;
    private final ClassifiedBy classifiedBy;
    private final Integer confidence;

    private ClassificationResult(Long categoryId, ClassifiedBy classifiedBy, Integer confidence) {
        this.categoryId = categoryId;
        this.classifiedBy = classifiedBy;
        this.confidence = confidence;
    }

    public static ClassificationResult ofRule(Long categoryId) {
        return new ClassificationResult(categoryId, ClassifiedBy.RULE, 100);
    }

    public static ClassificationResult ofAi(Long categoryId, Integer confidence) {
        return new ClassificationResult(categoryId, ClassifiedBy.AI, confidence);
    }

    // confidence < 60 이거나 GPT 분류 실패 시 → 기타지출(id=11)로 fallback
    public static ClassificationResult ofFallback() {
        return new ClassificationResult(11L, ClassifiedBy.AI, 0);
    }
}
