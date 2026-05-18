package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "manual_asset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManualAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private ManualAssetType assetType;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 255)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static ManualAsset create(Long userId, ManualAssetType assetType, Long amount, String memo) {
        ManualAsset asset = new ManualAsset();
        asset.userId = userId;
        asset.assetType = assetType;
        asset.amount = amount;
        asset.memo = memo;
        return asset;
    }

    public void update(ManualAssetType assetType, Long amount, String memo) {
        this.assetType = assetType;
        this.amount = amount;
        this.memo = memo;
    }
}
