package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetClass;
import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.RecommendedAssetsVo;
import com.project.flowfinserver.domain.ZeroReason;
import com.project.flowfinserver.dto.portfolio.InvestableAmountResult;
import com.project.flowfinserver.dto.portfolio.PortfolioAiInput;
import com.project.flowfinserver.dto.portfolio.PortfolioAiResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioAllocationItem;
import com.project.flowfinserver.dto.portfolio.PortfolioAllocationResponse;
import com.project.flowfinserver.dto.portfolio.PortfolioContext;
import com.project.flowfinserver.dto.portfolio.PortfolioRecommendResponse;
import com.project.flowfinserver.exception.PortfolioRecommendFailException;
import com.project.flowfinserver.exception.PortfolioValidationException;
import com.project.flowfinserver.openai.OpenAiPortfolioClient;
import com.project.flowfinserver.repository.PortfolioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final String FIXED_DISCLAIMER =
            "본 정보는 AI가 작성했으며 투자 조언이 아니라 정보 제공 목적이고, 투자 판단과 책임은 본인에게 있습니다.";

    private static final int MAX_ATTEMPTS = 2;

    private static final Set<String> VALID_ASSET_CLASSES = Arrays.stream(AssetClass.values())
            .map(AssetClass::getDisplayName)
            .collect(Collectors.toUnmodifiableSet());

    private static final Pattern SPECIFIC_PRODUCT_PATTERN = Pattern.compile(
            "\\b\\d{5,6}\\b|" +
            "\\b(TIGER|KODEX|KBSTAR|ARIRANG|HANARO|KOSEF|TIMEFOLIO|SOL|ACE|SMART|FOCUS)\\b"
    );

    private final OpenAiPortfolioClient openAiPortfolioClient;
    private final PortfolioRepository portfolioRepository;

    // ── 추천 흐름 ──────────────────────────────────────────────────────────────

    public PortfolioRecommendResponse recommend(PortfolioAiInput input, InvestableAmountResult investable) {
        if (!investable.assetLinked()) {
            return buildNeedAssetLinkResponse();
        }

        PortfolioValidationException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PortfolioAiResponse aiResponse = openAiPortfolioClient.recommend(input);
                return processResponse(aiResponse, investable);
            } catch (PortfolioValidationException e) {
                log.warn("[Portfolio] 검증 실패 attempt={}/{} reason={}", attempt, MAX_ATTEMPTS, e.getMessage());
                lastError = e;
            }
        }

        throw new PortfolioRecommendFailException("포트폴리오 추천 검증 최종 실패", lastError);
    }

    // ── DB 접근 (워커에서 호출, REQUIRES_NEW 트랜잭션) ──────────────────────────

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public PortfolioContext fetchForRecommend(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId.intValue())
                .orElseThrow(() -> new EntityNotFoundException("Portfolio not found: " + portfolioId));
        return new PortfolioContext(portfolioId, portfolio.getUserId(),
                portfolio.getPortfolioRiskType(), portfolio.getInvestableAmount());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completePortfolio(Long portfolioId, PortfolioRecommendResponse response) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId.intValue())
                .orElseThrow(() -> new EntityNotFoundException("Portfolio not found: " + portfolioId));
        portfolio.complete(toRecommendedAssetsVo(response), response.summary(), response.aiDiagnosis());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failPortfolio(Long portfolioId, String reason) {
        portfolioRepository.findById(portfolioId.intValue())
                .ifPresent(p -> p.fail(reason));
    }

    // ── 내부 검증/조립 ─────────────────────────────────────────────────────────

    private PortfolioRecommendResponse processResponse(PortfolioAiResponse aiResponse, InvestableAmountResult investable) {
        validateAssetClasses(aiResponse.allocation());
        List<PortfolioAllocationItem> normalized = normalizeRatios(aiResponse.allocation());
        List<PortfolioAllocationItem> sanitized = sanitizeSubCategories(normalized);
        List<PortfolioAllocationResponse> allocation = toAllocationResponse(sanitized, investable.amount());
        return buildResponse(investable, aiResponse, allocation);
    }

    private void validateAssetClasses(List<PortfolioAllocationItem> allocation) {
        if (allocation == null || allocation.isEmpty()) {
            throw new PortfolioValidationException("allocation이 null이거나 비어 있음");
        }
        allocation.forEach(item -> {
            if (!VALID_ASSET_CLASSES.contains(item.assetClass())) {
                throw new PortfolioValidationException("유효하지 않은 asset_class: " + item.assetClass());
            }
            if (item.ratio() == null) {
                throw new PortfolioValidationException("ratio가 null임 (asset_class=" + item.assetClass() + ")");
            }
            if (item.ratio() < 0) {
                throw new PortfolioValidationException("음수 ratio 감지: " + item.ratio() + " (asset_class=" + item.assetClass() + ")");
            }
        });
    }

    private List<PortfolioAllocationItem> normalizeRatios(List<PortfolioAllocationItem> allocation) {
        int total = allocation.stream().mapToInt(PortfolioAllocationItem::ratio).sum();

        if (total < 98 || total > 102) {
            throw new PortfolioValidationException("ratio 합계 범위 초과: " + total);
        }

        if (total == 100) {
            return allocation;
        }

        int diff = 100 - total;
        int maxIdx = findMaxRatioIndex(allocation);

        List<PortfolioAllocationItem> result = new ArrayList<>(allocation);
        PortfolioAllocationItem item = result.get(maxIdx);
        int newRatio = item.ratio() + diff;

        if (newRatio < 0) {
            throw new PortfolioValidationException("정규화 후 음수 ratio 발생: " + newRatio);
        }

        result.set(maxIdx, new PortfolioAllocationItem(
                item.assetClass(), item.subCategory(), newRatio, item.reason()
        ));
        return result;
    }

    private int findMaxRatioIndex(List<PortfolioAllocationItem> allocation) {
        int maxIdx = 0;
        for (int i = 1; i < allocation.size(); i++) {
            if (allocation.get(i).ratio() > allocation.get(maxIdx).ratio()) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private List<PortfolioAllocationItem> sanitizeSubCategories(List<PortfolioAllocationItem> allocation) {
        return allocation.stream()
                .map(item -> {
                    String sub = item.subCategory();
                    if (sub != null && !sub.isBlank()
                            && SPECIFIC_PRODUCT_PATTERN.matcher(sub).find()) {
                        log.debug("[Portfolio] sub_category 제거 → 대분류 대체: '{}' → '{}'", sub, item.assetClass());
                        return new PortfolioAllocationItem(
                                item.assetClass(), item.assetClass(), item.ratio(), item.reason()
                        );
                    }
                    return item;
                })
                .toList();
    }

    private List<PortfolioAllocationResponse> toAllocationResponse(
            List<PortfolioAllocationItem> allocation, long investableAmount) {
        return allocation.stream()
                .map(item -> new PortfolioAllocationResponse(
                        item.assetClass(),
                        item.subCategory(),
                        item.ratio(),
                        Math.round(investableAmount * (double) item.ratio() / 100),
                        item.reason()
                ))
                .toList();
    }

    private PortfolioRecommendResponse buildResponse(
            InvestableAmountResult investable,
            PortfolioAiResponse aiResponse,
            List<PortfolioAllocationResponse> allocation) {
        return new PortfolioRecommendResponse(
                investable.amount(),
                investable.assetLinked(),
                false,
                investable.zeroReason(),
                investable.fixedCostMissing(),
                aiResponse.riskType(),
                aiResponse.summary(),
                aiResponse.aiDiagnosis(),
                allocation,
                FIXED_DISCLAIMER
        );
    }

    private PortfolioRecommendResponse buildNeedAssetLinkResponse() {
        return new PortfolioRecommendResponse(
                0L, false, true, ZeroReason.NONE, false,
                null, null, null, List.of(), FIXED_DISCLAIMER
        );
    }

    private RecommendedAssetsVo toRecommendedAssetsVo(PortfolioRecommendResponse response) {
        if (response.allocation() == null || response.allocation().isEmpty()) {
            return new RecommendedAssetsVo(List.of());
        }
        List<RecommendedAssetsVo.AssetAllocation> allocations = response.allocation().stream()
                .map(a -> new RecommendedAssetsVo.AssetAllocation(
                        a.assetClass(), a.subCategory(), a.ratio(), a.amount(), a.reason()
                ))
                .toList();
        return new RecommendedAssetsVo(allocations);
    }
}
