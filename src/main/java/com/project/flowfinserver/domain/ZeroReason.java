package com.project.flowfinserver.domain;

//투자 가능 금액(investable_amount)의 0 타입
public enum ZeroReason {
    NONE,               // 정상 결과 0이 아닌 양수
    CALCULATED_ZERO,    // 계산 결과가 0
    CLAMPED             // 결과 음수인 0
}
