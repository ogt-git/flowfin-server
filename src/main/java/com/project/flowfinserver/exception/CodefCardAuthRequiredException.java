package com.project.flowfinserver.exception;

// 카드 청구내역 조회 시 카드번호/비밀번호 추가 인증이 필요한 경우 (CF-12108, CF-12401)
// is_active 변경 없이 422로 응답 — 프론트가 모달 입력 후 재호출
public class CodefCardAuthRequiredException extends RuntimeException {

    private final String codefCode;

    public CodefCardAuthRequiredException(String codefCode) {
        super("카드 추가 인증이 필요합니다.");
        this.codefCode = codefCode;
    }

    public String getCodefCode() {
        return codefCode;
    }
}
