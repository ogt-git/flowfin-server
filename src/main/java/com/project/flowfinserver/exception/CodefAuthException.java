package com.project.flowfinserver.exception;

// CODEF 인증 실패(CF-04xxx 계열) 전용 예외 — AUTH_ERROR 시 updateAccount 재시도 트리거용
public class CodefAuthException extends RuntimeException {

    public CodefAuthException(String message) {
        super(message);
    }

    public CodefAuthException() {
        super(ErrorCode.CODEF_AUTH_FAILED.getMessage());
    }
}
