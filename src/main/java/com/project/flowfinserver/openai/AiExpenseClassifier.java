package com.project.flowfinserver.openai;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.exception.OpenAiClassificationException;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Rule 분류 실패 Expense에 대해 OpenAI gpt-4.1-nano 호출 후 category 업데이트.
 * 반드시 외부 Bean에서 호출해야 @Async 프록시가 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExpenseClassifier {

    private static final long FALLBACK_CATEGORY_ID = 11L;
    private static final long CACHE_TTL_DAYS = 7;

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final OpenAiClassificationClient openAiClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Async("aiClassificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void classifyAndUpdate(Long expenseId) {
        // 1. 재조회 + 가드
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense == null) return;
        if (expense.isUserModified()) return;
        if (expense.getClassifiedBy() != ClassifiedBy.PENDING) return;

        String merchantName = expense.getMerchantName();
        String normalizedName = normalize(merchantName);
        String cacheKey = "category:merchant:" + sha256(normalizedName);

        // 2. Redis 캐시 히트
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            String[] parts = cached.split(":");
            Long categoryId = Long.parseLong(parts[0]);
            int confidence = parts.length > 1 ? Integer.parseInt(parts[1]) : 100;
            Category category = categoryRepository.findById(categoryId).orElseGet(this::fallbackCategory);

            // race condition 최종 방어
            Expense fresh = expenseRepository.findById(expenseId).orElse(null);
            if (fresh == null || fresh.isUserModified()) return;
            fresh.updateCategory(category, ClassifiedBy.AI, confidence);
            log.debug("[AiClassify] 캐시 히트 expenseId={} categoryId={} confidence={}", expenseId, categoryId, confidence);
            return;
        }

        // 3. OpenAI 호출
        long amount = Math.abs(expense.getAmount());
        ClassificationResult result;
        try {
            result = openAiClient.classify(normalizedName, amount);
        } catch (OpenAiClassificationException e) {
            log.warn("[AiClassify] 분류 최종 실패 expenseId={} merchant={}", expenseId, merchantName, e);
            applyFallback(expenseId);
            return;
        }

        // 4. Redis 캐싱 (카테고리 ID:confidence, TTL 7일)
        if (result.getCategory() != null) {
            String cacheValue = result.getCategory().getId() + ":" + result.getConfidence();
            stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, CACHE_TTL_DAYS, TimeUnit.DAYS);
        }

        // 5. race condition 최종 방어 후 UPDATE
        Expense fresh = expenseRepository.findById(expenseId).orElse(null);
        if (fresh == null || fresh.isUserModified()) return;
        fresh.updateCategory(result.getCategory(), result.getClassifiedBy(), result.getConfidence());

        log.debug("[AiClassify] 분류 완료 expenseId={} categoryId={} confidence={}",
                expenseId,
                result.getCategory() != null ? result.getCategory().getId() : null,
                result.getConfidence());
    }

    /**
     * 큐 포화(AbortPolicy 거부) 시 호출부에서 직접 기타지출로 동기 확정.
     * REQUIRES_NEW로 별도 트랜잭션을 보장한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallbackToEtc(Long expenseId) {
        applyFallback(expenseId);
    }

    private void applyFallback(Long expenseId) {
        Category fallback = fallbackCategory();
        Expense fresh = expenseRepository.findById(expenseId).orElse(null);
        if (fresh == null || fresh.isUserModified()) return;
        fresh.updateCategory(fallback, ClassifiedBy.AI, 0);
    }

    private Category fallbackCategory() {
        return categoryRepository.findById(FALLBACK_CATEGORY_ID)
                .orElseThrow(() -> new IllegalStateException("기타지출 카테고리(id=11)가 DB에 없습니다"));
    }

    private String normalize(String merchantName) {
        if (merchantName == null) return "";
        return merchantName.trim().replaceAll("\\s+", " ");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘 사용 불가", e);
        }
    }
}
