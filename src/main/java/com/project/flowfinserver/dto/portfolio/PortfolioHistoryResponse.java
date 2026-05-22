package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.PortfolioStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PortfolioHistoryResponse(
        @JsonProperty("portfolio_id") Long portfolioId,
        PortfolioStatus status,
        @JsonProperty("portfolio_risk_type") String portfolioRiskType,
        @JsonProperty("investable_amount") Long investableAmount,
        List<PortfolioAllocationResponse> allocation,
        String summary,
        @JsonProperty("ai_diagnosis") String aiDiagnosis,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static PortfolioHistoryResponse from(Portfolio portfolio) {
        List<PortfolioAllocationResponse> allocation =
                portfolio.getRecommendedAssets() == null
                || portfolio.getRecommendedAssets().getAllocations() == null
                ? List.of()
                : portfolio.getRecommendedAssets().getAllocations().stream()
                        .map(a -> new PortfolioAllocationResponse(
                                a.getAssetClass(), a.getSubCategory(),
                                a.getRatio(), a.getAmount(), a.getReason()))
                        .toList();

        return new PortfolioHistoryResponse(
                portfolio.getId().longValue(),
                portfolio.getStatus(),
                portfolio.getPortfolioRiskType(),
                portfolio.getInvestableAmount(),
                allocation,
                portfolio.getSummary(),
                portfolio.getAiDiagnosis(),
                portfolio.getCreatedAt()
        );
    }
}
