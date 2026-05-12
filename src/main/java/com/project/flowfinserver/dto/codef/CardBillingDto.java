package com.project.flowfinserver.dto.codef;

import java.time.LocalDateTime;

public record CardBillingDto(
        String cardCompany,
        Long amount,
        String merchantName,
        LocalDateTime expenseDate
) {}
