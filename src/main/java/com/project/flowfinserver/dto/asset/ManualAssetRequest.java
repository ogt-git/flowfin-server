package com.project.flowfinserver.dto.asset;

import com.project.flowfinserver.domain.ManualAssetType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ManualAssetRequest(

        @NotNull(message = "자산 유형은 필수입니다.")
        ManualAssetType assetType,

        @NotBlank(message = "자산명은 필수입니다.")
        @Size(max = 100, message = "자산명은 100자 이하여야 합니다.")
        String itemName,

        @NotNull(message = "취득가액은 필수입니다.")
        @Min(value = 0, message = "취득가액은 0원 이상이어야 합니다.")
        Long purchaseAmount,

        @NotNull(message = "현재가액은 필수입니다.")
        @Min(value = 0, message = "현재가액은 0원 이상이어야 합니다.")
        Long valuationAmt,

        LocalDate purchaseDate,

        @Size(max = 255, message = "메모는 255자 이하여야 합니다.")
        String memo
) {}
