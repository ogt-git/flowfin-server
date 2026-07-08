package com.project.flowfinserver.exception;

public class KoreaEximBankException extends RuntimeException {

    public KoreaEximBankException(String message) {
        super(message);
    }

    public KoreaEximBankException(String message, Throwable cause) {
        super(message, cause);
    }
}
