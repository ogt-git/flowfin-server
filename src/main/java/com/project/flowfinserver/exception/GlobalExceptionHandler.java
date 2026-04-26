package com.project.flowfinserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 중복 핸들러 제거 — 같은 예외 타입에 @ExceptionHandler 두 개는 Spring 초기화 실패 유발
    @ExceptionHandler(CodefAccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCodefAccountNotFound(CodefAccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, e.getMessage()));
    }

    @ExceptionHandler(CodefSyncException.class)
    public ResponseEntity<ErrorResponse> handleCodefSync(CodefSyncException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(502, e.getMessage()));
    }

    @ExceptionHandler(CodefApiException.class)
    public ResponseEntity<ErrorResponse> handleCodefApi(CodefApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(502, "[" + e.getCodefCode() + "] " + e.getMessage()));
    }

    @ExceptionHandler(DuplicateExpenseException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateExpense(DuplicateExpenseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "서버 오류가 발생했습니다."));
    }
}
