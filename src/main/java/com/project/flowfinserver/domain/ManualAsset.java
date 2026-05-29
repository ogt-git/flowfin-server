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

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "purchase_amount")
    private Long purchaseAmount;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "purchase_date")
    private java.time.LocalDate purchaseDate;

    @Column(length = 255)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static ManualAsset create(Long userId, ManualAssetType assetType, String itemName,
                                     Long purchaseAmount, Long amount, java.time.LocalDate purchaseDate, String memo) {
        ManualAsset asset = new ManualAsset();
        asset.userId = userId;
        asset.assetType = assetType;
        asset.itemName = itemName;
        asset.purchaseAmount = purchaseAmount;
        asset.amount = amount;
        asset.purchaseDate = purchaseDate;
        asset.memo = memo;
        return asset;
    }

    public void update(ManualAssetType assetType, String itemName, Long purchaseAmount,
                       Long amount, java.time.LocalDate purchaseDate, String memo) {
        this.assetType = assetType;
        this.itemName = itemName;
        this.purchaseAmount = purchaseAmount;
        this.amount = amount;
        this.purchaseDate = purchaseDate;
        this.memo = memo;
    }
}
