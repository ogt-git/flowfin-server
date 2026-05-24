package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.Portfolio;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class PortfolioResponse {

    private final Integer id;
    private final String portfolioRiskType;
    private final Map<String, Object> recommendedAssets;
    private final Long investableAmount;
    private final LocalDateTime createdAt;

    public PortfolioResponse(Portfolio portfolio) {
        this.id = portfolio.getId();
        this.portfolioRiskType = portfolio.getPortfolioRiskType();
        this.recommendedAssets = portfolio.getRecommendedAssets();
        this.investableAmount = portfolio.getInvestableAmount();
        this.createdAt = portfolio.getCreatedAt();
    }
}
