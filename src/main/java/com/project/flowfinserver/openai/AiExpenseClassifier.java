package com.project.flowfinserver.openai;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.exception.OpenAiClassificationException;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Rule 분류 실패 Expense에 대해 OpenAI gpt-4.1-nano 호출 후 category 업데이트.
 * 반드시 외부 Bean에서 호출해야 @Async 프록시가 동작한다.
 * TX 구조: ① 조회(readOnly REQUIRES_NEW) → ② OpenAI 호출(no TX) → ③ 저장(REQUIRES_NEW)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExpenseClassifier {

    private static final long FALLBACK_CATEGORY_ID = 11L;
    private static final long CACHE_TTL_DAYS = 7;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final OpenAiClassificationClient openAiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ExpenseStatsCacheManager expenseStatsCacheManager;

    // self-injection: 같은 클래스 내 @Transactional 메서드를 프록시를 통해 호출하기 위함
    @Lazy
    @Autowired
    private AiExpenseClassifier self;

    @Async("aiClassificationExecutor")
    public void classifyAndUpdate(Long expenseId) {
        // ① 조회 TX — 필요한 데이터만 추출 후 즉시 커넥션 반환
        ExpenseClassifyData data = self.loadExpenseData(expenseId);
        if (data == null) return;

        // Redis 캐시 히트 — OpenAI 호출 없이 바로 저장 TX로
        String cached = stringRedisTemplate.opsForValue().get(data.cacheKey());
        if (cached != null) {
            self.applyFromCache(expenseId, cached);
            return;
        }

        // ② OpenAI 호출 (TX 없음) — DB 커넥션 비점유 상태에서 실행
        ClassificationResult result;
        try {
            result = openAiClient.classify(data.normalizedName(), data.amount());
        } catch (OpenAiClassificationException e) {
            log.warn("[AiClassify] 분류 최종 실패 expenseId={} merchant={}", expenseId, data.normalizedName(), e);
            self.fallbackToEtc(expenseId);
            return;
        }

        // Redis 캐싱 (TX 불필요)
        if (result.getCategory() != null) {
            String cacheValue = result.getCategory().getId() + ":" + result.getConfidence();
            stringRedisTemplate.opsForValue().set(data.cacheKey(), cacheValue, CACHE_TTL_DAYS, TimeUnit.DAYS);
        }

        // ③ 저장 TX
        self.saveClassificationResult(expenseId, result);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public ExpenseClassifyData loadExpenseData(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense == null || expense.isUserModified() || expense.getClassifiedBy() != ClassifiedBy.PENDING) {
            return null;
        }
        String normalizedName = normalize(expense.getMerchantName());
        String cacheKey = "category:merchant:" + sha256(normalizedName);
        return new ExpenseClassifyData(normalizedName, Math.abs(expense.getAmount()), cacheKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyFromCache(Long expenseId, String cached) {
        String[] parts = cached.split(":");
        Long categoryId = Long.parseLong(parts[0]);
        int confidence = parts.length > 1 ? Integer.parseInt(parts[1]) : 100;
        Category category = categoryRepository.findById(categoryId).orElseGet(this::fallbackCategory);

        Expense fresh = expenseRepository.findById(expenseId).orElse(null);
        if (fresh == null || fresh.isUserModified()) return;
        fresh.updateCategory(category, ClassifiedBy.AI, confidence);
        expenseStatsCacheManager.evict(fresh.getUserId(), fresh.getExpenseDate().format(MONTH_FMT));
        log.debug("[AiClassify] 캐시 히트 expenseId={} categoryId={} confidence={}", expenseId, categoryId, confidence);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveClassificationResult(Long expenseId, ClassificationResult result) {
        Expense fresh = expenseRepository.findById(expenseId).orElse(null);
        if (fresh == null || fresh.isUserModified()) return;
        fresh.updateCategory(result.getCategory(), result.getClassifiedBy(), result.getConfidence());
        expenseStatsCacheManager.evict(fresh.getUserId(), fresh.getExpenseDate().format(MONTH_FMT));
        log.debug("[AiClassify] 분류 완료 expenseId={} categoryId={} confidence={}",
                expenseId,
                result.getCategory() != null ? result.getCategory().getId() : null,
                result.getConfidence());
    }

    /**
     * 큐 포화(AbortPolicy 거부) 시 호출부에서 직접 기타지출로 동기 확정.
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
        expenseStatsCacheManager.evict(fresh.getUserId(), fresh.getExpenseDate().format(MONTH_FMT));
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

    public record ExpenseClassifyData(String normalizedName, long amount, String cacheKey) {}
}
