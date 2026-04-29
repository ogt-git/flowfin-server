package com.project.flowfinserver.exception;

// 동일 지출 내역 중복 저장 시도 시 사용 (uq_expense 제약 위반 전 사전 방어)
public class DuplicateExpenseException extends RuntimeException {

    public DuplicateExpenseException(String message) {
        super(message);
    }
}
