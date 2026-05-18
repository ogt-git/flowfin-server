package com.project.flowfinserver.exception;

public enum CodefErrorType {
    /** 인증 실패 — updateAccount 호출로 복구 시도 가능 */
    AUTH_ERROR,
    /** 계정 잠금 등 — updateAccount로도 복구 불가, 연동 비활성화 */
    AUTH_UNRECOVERABLE,
    /** 일시적 오류 — 배치 재시도 대상 */
    TRANSIENT_ERROR,
    /** 요청 한도 초과 — 즉시 건너뜀 */
    RATE_LIMIT_ERROR,
    /** 영구 오류 — 연동 비활성화 */
    PERMANENT_ERROR,
    /** 미분류 오류 — 연동 비활성화 */
    UNKNOWN
}

