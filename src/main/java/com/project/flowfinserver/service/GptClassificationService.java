package com.project.flowfinserver.service;

import com.project.flowfinserver.dto.ClassificationResult;

/**
 * GPT-4 기반 AI 지출 카테고리 분류 인터페이스.
 * 구현체는 6주차에 OpenAI API 연동으로 완성 예정.
 * Rule-based 분류 실패 시 ExpenseClassificationService에서 이 인터페이스로 위임된다.
 *
 * GPT 응답 형식: {"category": "식비", "confidence": 85}
 * confidence < 60 이면 구현체 내부에서 ClassificationResult.ofFallback() 반환.
 */
public interface GptClassificationService {

    ClassificationResult classify(String merchantName, Long amount);
}
