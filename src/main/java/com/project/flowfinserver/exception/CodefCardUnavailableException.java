package com.project.flowfinserver.exception;

// 카드 해지(CF-13101) / 사용정지(CF-13110) — connectedId는 유효, 해당 카드만 스킵
public class CodefCardUnavailableException extends RuntimeException {

    private final String codefCode;

    public CodefCardUnavailableException(String codefCode) {
        super("카드 해지/정지: " + codefCode);
        this.codefCode = codefCode;
    }

    public String getCodefCode() {
        return codefCode;
    }
}
