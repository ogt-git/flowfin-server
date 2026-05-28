package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetClass;
import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.PortfolioStatus;
import com.project.flowfinserver.domain.RiskType;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.domain.ZeroReason;
import com.project.flowfinserver.dto.portfolio.PortfolioAllocationResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioHistoryResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioRecommendResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioRecommendResult;
import com.project.flowfinserver.dto.portfolio.PortfolioStatusResponse;
import com.project.flowfinserver.dto.portfolio.InvestableAmountResult;
import com.project.flowfinserver.exception.RiskTypeNotSetException;
import com.project.flowfinserver.exception.TooManyRequestsException;
import com.project.flowfinserver.repository.PortfolioRepository;
import com.project.flowfinserver.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioFacadeService {

    private static final String FIXED_DISCLAIMER =
            "본 정보는 AI가 작성했으며 투자 조언이 아니라 정보 제공 목적이고, 투자 판단과 책임은 본인에게 있습니다.";
    private static final String LOCK_KEY_PREFIX = "portfolio:lock:";
    // 최악 실행시간: OpenAI 3회 재시도(187s) × MAX_ATTEMPTS(1) ≈ 187s → 10분으로 충분한 여유 확보
    private static final long LOCK_TTL_MINUTES = 10L;
    private static final long COOLDOWN_MINUTES = 5L;
    private static final long CACHE_HOURS = 24L;

    private final PortfolioService portfolioService;
    private final PortfolioAsyncWorker portfolioAsyncWorker;
    private final AssetService assetService;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // ── POST /api/portfolio/recommend ─────────────────────────────────────────

    @Transactional
    public PortfolioRecommendResult requestRecommend(Long userId) {
        // 1. User 조회 — riskType은 회원가입 시 저장된 값 사용
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (user.getRiskType() == null) {
            throw new RiskTypeNotSetException();
        }

        // 2. 자산 연동 검사 — 미연동 시 즉시 반환 (PENDING 생성·GPT 호출 없음)
        InvestableAmountResult investable = assetService.calculateInvestableAmount(userId);
        if (!investable.assetLinked()) {
            return PortfolioRecommendResult.needAssetLink();
        }

        // 3. 24h 캐시 히트 검사 — 락·쿨다운 건너뜀
        String currentHash = buildInputHash(user.getRiskType(), investable.amount());
        Optional<Portfolio> latestCompleted = portfolioRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, PortfolioStatus.COMPLETED);
        if (latestCompleted.isPresent()) {
            Portfolio cached = latestCompleted.get();
            if (cached.getCreatedAt().isAfter(LocalDateTime.now().minusHours(CACHE_HOURS))
                    && currentHash.equals(cached.getRecommendInputHash())) {
                log.debug("[Portfolio] 캐시 히트 userId={} portfolioId={}", userId, cached.getId());
                return PortfolioRecommendResult.cacheHit(toRecommendResponse(cached));
            }
        }

        // 4. 동시요청 Redis 락 — UUID 토큰으로 소유권 식별
        String lockKey = LOCK_KEY_PREFIX + userId;
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL_MINUTES, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(locked)) {
            throw new TooManyRequestsException("이미 추천 생성 중입니다. 잠시 후 다시 시도해주세요.");
        }

        // 5~6. 쿨다운 검사 + PENDING 생성: 예외 시 락 즉시 해제
        try {
            portfolioRepository.findTopByUserIdOrderByCreatedAtDesc(userId).ifPresent(latest -> {
                if (latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES))) {
                    throw new TooManyRequestsException("추천 요청은 5분에 한 번만 가능합니다.");
                }
            });

            // 6. PENDING 레코드 INSERT (investable_amount, portfolio_risk_type, hash 스냅샷)
            Portfolio pending = Portfolio.createPending(
                    userId, user.getRiskType(), investable.amount(), currentHash);
            portfolioRepository.save(pending);
            Long portfolioId = pending.getId().longValue();

            // 8. PENDING 커밋 완료 후 워커 트리거 — lockToken 캡처해 소유권 전달
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        portfolioAsyncWorker.executeRecommendation(portfolioId, lockToken);
                    } catch (RuntimeException e) {
                        // executor 큐 포화(RejectedExecutionException) 등 제출 자체 실패 시 FAILED 전환 + 락 해제
                        log.error("[Portfolio] 워커 제출 실패 — FAILED 전환 userId={} portfolioId={}", userId, portfolioId, e);
                        portfolioService.failPortfolio(portfolioId, "executor 큐 포화로 추천 처리 불가");
                        deleteLockIfOwner(lockKey, lockToken);
                        throw e;
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // 트랜잭션 커밋 실패(롤백) 또는 결과 불명 시 락 즉시 해제
                    if (status == STATUS_ROLLED_BACK || status == STATUS_UNKNOWN) {
                        log.warn("[Portfolio] 트랜잭션 비정상 종료(status={}) — 락 해제 userId={}", status, userId);
                        deleteLockIfOwner(lockKey, lockToken);
                    }
                }
            });

            log.info("[Portfolio] PENDING 생성 userId={} portfolioId={}", userId, portfolioId);
            // 7. 202 Accepted
            return PortfolioRecommendResult.accepted(portfolioId);

        } catch (Exception e) {
            deleteLockIfOwner(lockKey, lockToken);
            throw e;
        }
    }

    private void deleteLockIfOwner(String lockKey, String lockToken) {
        String current = stringRedisTemplate.opsForValue().get(lockKey);
        if (lockToken.equals(current)) {
            stringRedisTemplate.delete(lockKey);
        }
    }

    // ── GET /api/portfolio ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortfolioStatusResponse getLatestPortfolio(Long userId) {
        Optional<Portfolio> latestOpt = portfolioRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if (latestOpt.isEmpty()) {
            return PortfolioStatusResponse.empty();
        }

        Portfolio latest = latestOpt.get();
        boolean pendingExists = latest.getStatus() == PortfolioStatus.PENDING;
        boolean canRecommend = !pendingExists
                && latest.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES));

        Optional<Portfolio> latestCompletedOpt = latest.getStatus() == PortfolioStatus.COMPLETED
                ? latestOpt
                : portfolioRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, PortfolioStatus.COMPLETED);

        LocalDateTime lastRecommendedAt = latestCompletedOpt.map(Portfolio::getCreatedAt).orElse(null);

        PortfolioRecommendResponse result = latestCompletedOpt.isPresent()
                && latest.getStatus() == PortfolioStatus.COMPLETED
                ? toRecommendResponse(latest)
                : null;

        return new PortfolioStatusResponse(
                latest.getId().longValue(),
                latest.getStatus(),
                canRecommend,
                pendingExists,
                lastRecommendedAt,
                result
        );
    }

    // ── GET /api/portfolio/history ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PortfolioHistoryResponse> getPortfolioHistory(Long userId) {
        return portfolioRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PortfolioHistoryResponse::from)
                .toList();
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    private PortfolioRecommendResponse toRecommendResponse(Portfolio portfolio) {
        List<PortfolioAllocationResponse> allocation =
                portfolio.getRecommendedAssets() == null
                || portfolio.getRecommendedAssets().getAllocations() == null
                ? List.of()
                : portfolio.getRecommendedAssets().getAllocations().stream()
                        .map(a -> new PortfolioAllocationResponse(
                                a.getAssetClass(), a.getSubCategory(),
                                a.getRatio(), a.getAmount(), a.getReason()))
                        .toList();

        return new PortfolioRecommendResponse(
                portfolio.getInvestableAmount(),
                true,
                false,
                ZeroReason.NONE,
                false,
                portfolio.getPortfolioRiskType(),
                portfolio.getSummary(),
                portfolio.getAiDiagnosis(),
                allocation,
                FIXED_DISCLAIMER
        );
    }

    /**
     * 해시 입력: riskType + investableAmount + AssetClass 고정 순서(없으면 ratio=0).
     * investableAmount가 바뀌면 캐시 무효화 — 투자 규모 변화가 추천 품질에 직접 영향.
     * currentAllocation은 현재 null 고정(resProductTypeCd 매핑 미확정).
     */
    private String buildInputHash(RiskType riskType, long investableAmount) {
        StringBuilder sb = new StringBuilder(riskType.name())
                .append("|").append(investableAmount).append("|");
        Arrays.stream(AssetClass.values()).forEach(ac ->
                sb.append(ac.getDisplayName()).append(":0|"));
        return sha256(sb.toString());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘 사용 불가", e);
        }
    }
}
