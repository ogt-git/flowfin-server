package com.project.flowfinserver.dto.exchangerate;

import com.project.flowfinserver.domain.ExchangeRate;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExchangeRateResponse {
    private String currencyCode;
    private String baseCurrency;
    private BigDecimal rate;
    private LocalDate baseDate;
    private String provider;
    private LocalDateTime fetchedAt;
    private boolean stale;

    public static ExchangeRateResponse from(ExchangeRate e, boolean stale) {
        return ExchangeRateResponse.builder()
                .currencyCode(e.getCurrencyCode())
                .baseCurrency(e.getBaseCurrency())
                .rate(e.getRate())
                .baseDate(e.getBaseDate())
                .provider(e.getProvider())
                .fetchedAt(e.getFetchedAt())
                .stale(stale)
                .build();
    }
}
