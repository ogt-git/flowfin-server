package com.project.flowfinserver.domain;

public enum ManualAssetType {
    DEPOSIT,      // 예금 — 유동성 자산 (investable_amount 포함)
    SAVINGS,      // 적금 — 유동성 자산 (investable_amount 포함)
    REAL_ESTATE,  // 부동산 — 비유동성 자산 (investable_amount 제외)
    CASH,         // 현금 — 유동성 자산 (investable_amount 포함)
    PENSION,      // 연금 -  IRP·퇴직연금·연금저축 (investable_amount 제외)
    ETC           // 기타 — 비유동성 자산 (investable_amount 제외)
}
