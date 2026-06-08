package com.project.flowfinserver.dto.portfolio;

import com.project.flowfinserver.domain.PortfolioStatus;

import java.time.LocalDateTime;

public record PortfolioStatusResponse(
        Long portfolioId,
        PortfolioStatus status,
        boolean canRecommend,
        boolean pendingExists,
        LocalDateTime lastRecommendedAt,
        PortfolioRecommendResponse result
) {
    public static PortfolioStatusResponse empty() {
        return new PortfolioStatusResponse(null, null, true, false, null, null);
    }
}
