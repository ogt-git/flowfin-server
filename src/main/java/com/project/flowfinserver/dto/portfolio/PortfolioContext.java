package com.project.flowfinserver.dto.portfolio;

public record PortfolioContext(
        Long portfolioId,
        Long userId,
        String riskType,
        Long investableAmount
) {}
