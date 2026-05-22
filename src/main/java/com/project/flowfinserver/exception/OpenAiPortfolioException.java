package com.project.flowfinserver.exception;

public class OpenAiPortfolioException extends RuntimeException {

    public OpenAiPortfolioException(String message) {
        super(message);
    }

    public OpenAiPortfolioException(String message, Throwable cause) {
        super(message, cause);
    }
}
