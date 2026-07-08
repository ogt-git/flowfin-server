package com.project.flowfinserver.exception;

public class ExchangeRateUnavailableException extends RuntimeException {

    private final String currencyCode;

    public ExchangeRateUnavailableException(String currencyCode) {
        super("환율 정보 없음: " + currencyCode);
        this.currencyCode = currencyCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
