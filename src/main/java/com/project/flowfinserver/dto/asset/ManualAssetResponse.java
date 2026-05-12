package com.project.flowfinserver.dto.asset;

import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;

import java.time.LocalDateTime;

public record ManualAssetResponse(
        Long id,
        ManualAssetType assetType,
        Long amount,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ManualAssetResponse from(ManualAsset asset) {
        return new ManualAssetResponse(
                asset.getId(),
                asset.getAssetType(),
                asset.getAmount(),
                asset.getMemo(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
