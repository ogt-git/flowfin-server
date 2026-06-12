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
    /** 금융기관 사이트 변경/점검 — 재시도·비활성화 없이 스킵 */
    INSTITUTION_UNAVAILABLE,
    /** 중복 로그인·부하 제한 — 즉시 재시도 금지, 연동 유지, 해당 회차 스킵 */
    COOLDOWN,
    /** 카드 해지/정지 — 해당 카드 스킵, 연동(connectedId)은 유지 */
    CARD_UNAVAILABLE,
    /** 조회 결과 없음 — 빈 결과 정상 반환 (에러 아님) */
    EMPTY_RESULT,
    /** 미분류 오류 — 연동 비활성화 */
    UNKNOWN
}

