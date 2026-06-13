package com.project.flowfinserver.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssetClass {
    KOREAN_STOCK("국내주식"),
    FOREIGN_STOCK("해외주식"),
    BOND("채권"),
    ETF("ETF"),
    REAL_ESTATE("부동산/리츠"),
    COMMODITY("원자재"),
    ALTERNATIVE("대체투자"),
    CASH("현금성자산"),
    ETC("기타");

    private final String displayName;
}
