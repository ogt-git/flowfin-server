package com.project.flowfinserver.exception;

public class OpenAiClassificationException extends RuntimeException {

    public OpenAiClassificationException(String message) {
        super(message);
    }

    public OpenAiClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
