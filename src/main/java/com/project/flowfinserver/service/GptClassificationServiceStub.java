package com.project.flowfinserver.service;

import com.project.flowfinserver.dto.ClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 6주차 OpenAI API 연동 전까지 사용하는 stub 구현체.
 * Rule-based 분류 실패 시 무조건 기타지출(id=11)로 fallback 처리한다.
 */
@Slf4j
@Service
public class GptClassificationServiceStub implements GptClassificationService {

    @Override
    public ClassificationResult classify(String merchantName, Long amount) {
        log.debug("[GPT-stub] Rule 분류 실패 → 기타지출 fallback merchantName={} amount={}", merchantName, amount);
        return ClassificationResult.ofFallback();
    }
}
