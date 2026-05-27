package com.project.flowfinserver.exception;

import com.project.flowfinserver.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("필수 헤더가 누락되었습니다: " + e.getHeaderName(), "MISSING_HEADER"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "INVALID_REQUEST"));
    }

    //데이터 중복 방지 핸들러
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMostSpecificCause().getMessage());
        String message = e.getMostSpecificCause().getMessage();
        String errorCode;
        if (message != null && message.contains("uq_expense")) {
            errorCode = ErrorCode.DUPLICATE_EXPENSE.name();
            message   = ErrorCode.DUPLICATE_EXPENSE.getMessage();
        } else {
            errorCode = "DUPLICATE_DATA";
            message   = "이미 존재하는 데이터입니다.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, errorCode));
    }

    //리소스 찾기 실패 핸들러
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleEntityNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage(), ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    //보안 및 권한 핸들러
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.getMessage(), ErrorCode.ACCESS_DENIED.name()));
    }

    //API 호출 제한 핸들러
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<?>> handleTooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage(), ErrorCode.TOO_MANY_REQUESTS.name()));
    }

    @ExceptionHandler(CodefAuthException.class)
    public ResponseEntity<ApiResponse<?>> handleCodefAuth(CodefAuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage(), ErrorCode.CODEF_AUTH_FAILED.name()));
    }

    @ExceptionHandler(CodefAccountNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleCodefAccountNotFound(CodefAccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage(), ErrorCode.CONNECTION_NOT_FOUND.name()));
    }

    @ExceptionHandler(CodefSyncException.class)
    public ResponseEntity<ApiResponse<?>> handleCodefSync(CodefSyncException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(e.getMessage(), ErrorCode.CODEF_SYNC_FAILED.name()));
    }

    //CODEF API 연동 장애 핸들러
    @ExceptionHandler(CodefApiException.class)
    public ResponseEntity<ApiResponse<?>> handleCodefApi(CodefApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("[" + e.getCodefCode() + "] " + e.getMessage(), ErrorCode.CODEF_SYNC_FAILED.name()));
    }

    // 제외 처리된 지출 카테고리 수정 시도 등 잘못된 상태 전환 → 400
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage(), "INVALID_STATE"));
    }

    // 날짜 범위 오류(startDate > endDate) 등 잘못된 파라미터 → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage(), "INVALID_ARGUMENT"));
    }

    @ExceptionHandler(CommunityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommunityNotFound(CommunityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, e.getMessage()));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("잘못된 파라미터 값입니다: " + e.getName() + "=" + e.getValue(), "INVALID_PARAMETER"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.name()));
    }
}
