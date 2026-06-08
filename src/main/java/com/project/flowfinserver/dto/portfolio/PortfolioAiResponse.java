package com.project.flowfinserver.dto.portfolio;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PortfolioAiResponse(
        @JsonProperty("risk_type") String riskType,
        String summary,
        @JsonProperty("ai_diagnosis") String aiDiagnosis,
        List<PortfolioAllocationItem> allocation,
        String disclaimer
) {}
