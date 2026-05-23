package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioAiInput(
        @JsonProperty("risk_type") String riskType,
        @JsonProperty("age") Integer age,
        @JsonProperty("investable_amount") Long investableAmount,
        @JsonProperty("total_asset") Long totalAsset,
        @JsonProperty("current_allocation") Map<String, Integer> currentAllocation,
        @JsonProperty("spending_summary") SpendingSummary spendingSummary
) {}
