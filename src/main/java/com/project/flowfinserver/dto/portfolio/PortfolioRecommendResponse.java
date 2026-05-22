package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.flowfinserver.domain.ZeroReason;

import java.util.List;

public record PortfolioRecommendResponse(
        @JsonProperty("investable_amount") Long investableAmount,
        boolean assetLinked,
        boolean needAssetLink,
        ZeroReason zeroReason,
        boolean fixedCostMissing,
        @JsonProperty("risk_type") String riskType,
        String summary,
        @JsonProperty("ai_diagnosis") String aiDiagnosis,
        List<PortfolioAllocationResponse> allocation,
        String disclaimer
) {}
