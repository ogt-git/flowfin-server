package com.project.flowfinserver.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ManualAssetType {
    DEPOSIT("예금"),      // 유동성 자산 (investable_amount 포함)
    SAVINGS("적금"),      // 비유동성 자산 (investable_amount 제외)
    REAL_ESTATE("부동산"), // 비유동성 자산 (investable_amount 제외)
    CASH("현금"),         // 유동성 자산 (investable_amount 포함)
    PENSION("연금"),      // IRP·퇴직연금·연금저축 (investable_amount 제외)
    ETC("기타");          // 비유동성 자산 (investable_amount 제외)

    private final String korean;

    ManualAssetType(String korean) {
        this.korean = korean;
    }

    @JsonCreator
    public static ManualAssetType from(String value) {
        for (ManualAssetType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.korean.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("유효하지 않은 자산 유형입니다: " + value);
    }
}
