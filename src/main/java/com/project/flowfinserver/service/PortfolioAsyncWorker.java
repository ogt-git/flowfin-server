package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.portfolio.InvestableAmountResult;
import com.project.flowfinserver.dto.portfolio.PortfolioAiInput;
import com.project.flowfinserver.dto.portfolio.PortfolioContext;
import com.project.flowfinserver.dto.portfolio.PortfolioRecommendResponse;
import com.project.flowfinserver.dto.portfolio.SpendingSummary;
import com.project.flowfinserver.exception.PortfolioRecommendFailException;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAsyncWorker {

    private static final String LOCK_KEY_PREFIX = "portfolio:lock:";

    private final PortfolioService portfolioService;
    private final AssetService assetService;
    private final AssetAccountRepository assetAccountRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Async("portfolioExecutor")
    public void executeRecommendation(Long portfolioId, String lockToken) {
        Long userId = null;
        try {
            PortfolioContext ctx = portfolioService.fetchForRecommend(portfolioId);
            userId = ctx.userId();

            // assetLinked·zeroReason·fixedCostMissing 플래그는 현재 상태 기준으로 판단
            InvestableAmountResult currentState = assetService.calculateInvestableAmount(userId);
            // investable_amount는 PENDING 생성 시점(T1) 스냅샷 사용 — 워커 실행 시점(T2) 재계산 값과 불일치 방지
            long snapshotAmount = ctx.investableAmount() != null ? ctx.investableAmount() : 0L;
            InvestableAmountResult investable = new InvestableAmountResult(
                    currentState.assetLinked(), snapshotAmount,
                    currentState.zeroReason(), currentState.fixedCostMissing());

            long totalAsset = assetAccountRepository.findAllByUserId(userId).stream()
                    .mapToLong(a -> a.getTotalAsset())
                    .sum();

            SpendingSummary spendingSummary = buildSpendingSummary(userId);

            PortfolioAiInput input = new PortfolioAiInput(
                    ctx.riskType(), null, snapshotAmount, totalAsset, null, spendingSummary);

            PortfolioRecommendResponse response = portfolioService.recommend(input, investable);
            portfolioService.completePortfolio(portfolioId, response);
            log.info("[Portfolio] 추천 완료 portfolioId={}", portfolioId);

        } catch (PortfolioRecommendFailException e) {
            log.warn("[Portfolio] 추천 최종 실패 portfolioId={} reason={}", portfolioId, e.getMessage());
            portfolioService.failPortfolio(portfolioId, truncate(e.getMessage()));
        } catch (Exception e) {
            log.error("[Portfolio] 워커 예외 portfolioId={}", portfolioId, e);
            portfolioService.failPortfolio(portfolioId, truncate(e.getMessage()));
        } finally {
            if (userId != null) {
                releaseLock(userId, portfolioId, lockToken);
            }
            // userId가 null인 경우(fetchForRecommend 실패)는 TTL 만료로 자동 해제
        }
    }

    private SpendingSummary buildSpendingSummary(Long userId) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<Long> fixedIds = categoryRepository.findByType(CategoryType.FIXED)
                .stream().map(c -> c.getId()).toList();
        List<Long> variableIds = categoryRepository.findByType(CategoryType.VARIABLE)
                .stream().map(c -> c.getId()).toList();

        List<Expense> fixedExpenses = fixedIds.isEmpty() ? List.of() :
                expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(userId, fixedIds, threeMonthsAgo);
        List<Expense> variableExpenses = variableIds.isEmpty() ? List.of() :
                expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(userId, variableIds, threeMonthsAgo);

        long fixedTotal = fixedExpenses.stream().mapToLong(e -> e.getAmount()).sum();
        long variableTotal = variableExpenses.stream().mapToLong(e -> e.getAmount()).sum();
        long grandTotal = fixedTotal + variableTotal;

        Integer fixedRatio = grandTotal > 0 ? (int) Math.round(fixedTotal * 100.0 / grandTotal) : null;

        LocalDateTime earliest = variableExpenses.stream()
                .map(e -> e.getExpenseDate())
                .min(Comparator.naturalOrder())
                .orElse(null);
        Long monthlyVariableAvg = null;
        if (!variableExpenses.isEmpty() && earliest != null) {
            long months = Math.max(1, ChronoUnit.MONTHS.between(earliest.toLocalDate(), LocalDateTime.now().toLocalDate()));
            monthlyVariableAvg = variableTotal / months;
        }

        return new SpendingSummary(fixedRatio, monthlyVariableAvg);
    }

    private void releaseLock(Long userId, Long portfolioId, String lockToken) {
        String lockKey = LOCK_KEY_PREFIX + userId;
        String current = stringRedisTemplate.opsForValue().get(lockKey);
        if (lockToken.equals(current)) {
            stringRedisTemplate.delete(lockKey);
            log.debug("[Portfolio] Redis 락 해제 userId={} portfolioId={}", userId, portfolioId);
        } else {
            log.warn("[Portfolio] 락 소유권 불일치 — 해제 건너뜀 userId={} portfolioId={} (TTL 만료 후 재발급된 락)", userId, portfolioId);
        }
    }

    private String truncate(String message) {
        if (message == null) return "";
        return message.length() > 255 ? message.substring(0, 255) : message;
    }
}
