package com.project.flowfinserver.exception;

public class PortfolioRecommendFailException extends RuntimeException {

    public PortfolioRecommendFailException(String message) {
        super(message);
    }

    public PortfolioRecommendFailException(String message, Throwable cause) {
        super(message, cause);
    }
}
