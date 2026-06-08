package com.project.flowfinserver.exception;

public class RiskTypeNotSetException extends RuntimeException {

    public RiskTypeNotSetException() {
        super("투자성향 설정이 필요합니다. POST /api/users/tendency를 먼저 호출해주세요.");
    }
}
