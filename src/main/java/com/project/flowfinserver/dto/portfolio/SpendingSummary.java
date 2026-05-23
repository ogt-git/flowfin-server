package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpendingSummary(
        @JsonProperty("fixed_ratio") Integer fixedRatio,
        @JsonProperty("monthly_variable_avg") Long monthlyVariableAvg
) {}
