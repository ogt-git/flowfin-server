package com.project.flowfinserver.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedAssetsVo {

    private List<AssetAllocation> allocations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetAllocation {
        private String assetClass; // 자산 분류
        private String subCategory; // 종목 분류
        private Integer ratio;  // 비율
        private Long amount;    // nullable — investable_amount 미연동 시 null
        private String reason;
    }
}
