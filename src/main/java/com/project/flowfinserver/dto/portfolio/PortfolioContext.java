package com.project.flowfinserver.dto.portfolio;

import com.project.flowfinserver.domain.RiskType;

public record PortfolioContext(
        Long portfolioId,
        Long userId,
        RiskType riskType,
        Long investableAmount
) {}
