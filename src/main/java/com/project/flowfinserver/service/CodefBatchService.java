package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.exception.CodefRetryableException;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodefBatchService {

    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final CodefSyncService codefSyncService;

    @Scheduled(cron = "0 0 2 * * *")
    public void syncAllActiveConnections() {
        List<CodefConnectedAccount> connections = connectedAccountRepository.findAllByIsActiveTrue();
        log.info("[Batch] CODEF 동기화 시작 — 활성 연동 수={}", connections.size());

        int success = 0, failure = 0;
        for (CodefConnectedAccount conn : connections) {
            boolean synced = false;

            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    codefSyncService.syncConnection(conn);
                    synced = true;
                    break;
                } catch (CodefRetryableException e) {
                    // 일시적 오류만 재시도
                    log.warn("[Batch] 일시 오류 재시도 userId={} org={} attempt={} code={}",
                            conn.getUserId(), conn.getOrganizationCode(), attempt, e.getErrorCode());
                } catch (Exception e) {
                    // 인증 오류, 영구 오류 등 — 즉시 중단 (handleCodefError에서 이미 처리됨)
                    log.warn("[Batch] 비재시도 오류 — 중단 userId={} org={} error={}",
                            conn.getUserId(), conn.getOrganizationCode(), e.getMessage());
                    break;
                }
            }

            if (synced) {
                success++;
            } else {
                codefSyncService.deactivateConnection(conn.getId());
                failure++;
                log.error("[Batch] 동기화 실패 — 연동 비활성화 connectionId={} userId={}",
                        conn.getId(), conn.getUserId());
            }
        }

        log.info("[Batch] CODEF 동기화 완료 — 성공={} 실패(비활성화)={}", success, failure);
    }
}
