package com.project.flowfinserver.dto.portfolio;

import com.project.flowfinserver.domain.ZeroReason;

public record InvestableAmountResult(
        boolean assetLinked,
        long amount,
        ZeroReason zeroReason,
        boolean fixedCostMissing
) {}
