package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate",
        indexes = {
                @Index(name = "idx_er_currency_date", columnList = "currency_code, base_date"),
                @Index(name = "idx_er_currency_fetched", columnList = "currency_code, fetched_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_er_currency_date", columnNames = {"currency_code", "base_date"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", length = 10, nullable = false)
    private String currencyCode;

    @Column(name = "base_currency", length = 10, nullable = false)
    private String baseCurrency;

    @Column(name = "rate", nullable = false, precision = 15, scale = 4)
    private BigDecimal rate;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "provider", length = 50, nullable = false)
    private String provider;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "original_currency_code", length = 20)
    private String originalCurrencyCode;

    @Column(name = "original_rate_str", length = 50)
    private String originalRateStr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ExchangeRate(String currencyCode, String baseCurrency, BigDecimal rate,
                        LocalDate baseDate, String provider, LocalDateTime fetchedAt,
                        String originalCurrencyCode, String originalRateStr) {
        this.currencyCode = currencyCode;
        this.baseCurrency = baseCurrency;
        this.rate = rate;
        this.baseDate = baseDate;
        this.provider = provider;
        this.fetchedAt = fetchedAt;
        this.originalCurrencyCode = originalCurrencyCode;
        this.originalRateStr = originalRateStr;
        this.createdAt = LocalDateTime.now();
    }

    public void updateRate(BigDecimal rate, LocalDateTime fetchedAt,
                           String originalCurrencyCode, String originalRateStr) {
        this.rate = rate;
        this.fetchedAt = fetchedAt;
        this.originalCurrencyCode = originalCurrencyCode;
        this.originalRateStr = originalRateStr;
    }
}
