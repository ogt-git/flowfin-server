package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.openai.AiExpenseClassifier;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingExpenseRetryScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int TIMEOUT_HOURS = 1;

    private final ExpenseRepository expenseRepository;
    private final AiExpenseClassifier aiExpenseClassifier;

    @Scheduled(fixedDelay = 30_000)
    public void retryPendingExpenses() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(TIMEOUT_HOURS);

        // 1시간 초과 PENDING → 기타지출 확정
        List<Long> timedOut = expenseRepository.findPendingIdsTimedOut(ClassifiedBy.PENDING, cutoff);
        if (!timedOut.isEmpty()) {
            log.warn("[PendingRetry] 1시간 초과 PENDING {}건 → 기타지출 확정", timedOut.size());
            for (Long id : timedOut) {
                aiExpenseClassifier.fallbackToEtc(id);
            }
        }

        // 1시간 이내 PENDING → AI 분류 재시도 (최대 50건, 오래된 순)
        List<Long> retryIds = expenseRepository.findPendingIdsForRetry(
                ClassifiedBy.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE));
        if (retryIds.isEmpty()) return;

        log.info("[PendingRetry] PENDING 재시도 {}건 큐 제출 시도", retryIds.size());
        int submitted = 0;
        for (Long id : retryIds) {
            try {
                aiExpenseClassifier.classifyAndUpdate(id);
                submitted++;
            } catch (TaskRejectedException e) {
                // 큐가 꽉 찼으면 남은 건은 다음 사이클에서 재처리
                log.debug("[PendingRetry] 큐 포화 → {}건 제출 후 중단, 다음 사이클 재시도", submitted);
                break;
            }
        }
        if (submitted > 0) {
            log.info("[PendingRetry] {}건 큐 제출 완료", submitted);
        }
    }
}
