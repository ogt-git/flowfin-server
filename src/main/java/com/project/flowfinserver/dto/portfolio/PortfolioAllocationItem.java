package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PortfolioAllocationItem(
        @JsonProperty("asset_class") String assetClass,
        @JsonProperty("sub_category") String subCategory,
        Integer ratio,
        String reason
) {}
