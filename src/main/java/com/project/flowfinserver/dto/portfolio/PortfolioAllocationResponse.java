package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PortfolioAllocationResponse(
        @JsonProperty("asset_class") String assetClass,
        @JsonProperty("sub_category") String subCategory,
        Integer ratio,
        Long amount,
        String reason
) {}
