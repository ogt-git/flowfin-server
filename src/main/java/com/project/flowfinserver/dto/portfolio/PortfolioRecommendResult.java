package com.project.flowfinserver.dto.portfolio;

public record PortfolioRecommendResult(
        Type type,
        Long portfolioId,
        PortfolioRecommendResponse cachedResponse
) {
    public enum Type {
        NEED_ASSET_LINK,
        CACHE_HIT,
        ACCEPTED
    }

    public static PortfolioRecommendResult needAssetLink() {
        return new PortfolioRecommendResult(Type.NEED_ASSET_LINK, null, null);
    }

    public static PortfolioRecommendResult cacheHit(PortfolioRecommendResponse response) {
        return new PortfolioRecommendResult(Type.CACHE_HIT, null, response);
    }

    public static PortfolioRecommendResult accepted(Long portfolioId) {
        return new PortfolioRecommendResult(Type.ACCEPTED, portfolioId, null);
    }
}
