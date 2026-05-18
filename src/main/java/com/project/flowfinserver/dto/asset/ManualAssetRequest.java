package com.project.flowfinserver.dto.asset;

import com.project.flowfinserver.domain.ManualAssetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualAssetRequest(

        @NotNull(message = "자산 유형은 필수입니다.")
        ManualAssetType assetType,

        @NotNull(message = "금액은 필수입니다.")
        @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
        Long amount,

        @Size(max = 255, message = "메모는 255자 이하여야 합니다.")
        String memo
) {}
