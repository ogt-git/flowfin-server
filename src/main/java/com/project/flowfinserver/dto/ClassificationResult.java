package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.Category; // CHANGED
import com.project.flowfinserver.domain.ClassifiedBy;
import lombok.Getter;

@Getter
public class ClassificationResult {

    private final Category category;      // CHANGED
    private final ClassifiedBy classifiedBy;
    private final Integer confidence;

    private ClassificationResult(Category category, ClassifiedBy classifiedBy, Integer confidence) { // CHANGED
        this.category = category; // CHANGED
        this.classifiedBy = classifiedBy;
        this.confidence = confidence;
    }

    public static ClassificationResult ofRule(Category category) { // CHANGED
        return new ClassificationResult(category, ClassifiedBy.RULE, 100);
    }

    public static ClassificationResult ofAi(Category category, Integer confidence) { // CHANGED
        return new ClassificationResult(category, ClassifiedBy.AI, confidence);
    }

    // confidence < 60 이거나 GPT 분류 실패 시 → 기타지출(id=11)로 fallback
    public static ClassificationResult ofFallback(Category fallbackCategory) { // CHANGED
        return new ClassificationResult(fallbackCategory, ClassifiedBy.AI, 0);
    }

    // Rule 분류 실패 → 트랜잭션 내 저장 후 비동기 AI 분류 대기 상태
    public static ClassificationResult pending() {
        return new ClassificationResult(null, ClassifiedBy.PENDING, null);
    }
}
