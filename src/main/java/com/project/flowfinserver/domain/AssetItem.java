package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_asset_item",
                columnNames = {"account_id", "item_code"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "item_code", length = 20)
    private String itemCode;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "purchase_amount")
    private Long purchaseAmount;

    @Column(name = "valuation_amt")
    private Long valuationAmt;

    @Column(name = "valuation_pl")
    private Long valuationPl;

    @Column(name = "earnings_rate", precision = 5, scale = 2)
    private BigDecimal earningsRate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public AssetItem(Long accountId, Long userId, String productType, String itemName,
                     String itemCode, Integer quantity, Long purchaseAmount,
                     Long valuationAmt, Long valuationPl, BigDecimal earningsRate) {
        this.accountId = accountId;
        this.userId = userId;
        this.productType = productType;
        this.itemName = itemName;
        this.itemCode = itemCode;
        this.quantity = quantity;
        this.purchaseAmount = purchaseAmount;
        this.valuationAmt = valuationAmt;
        this.valuationPl = valuationPl;
        this.earningsRate = earningsRate;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Integer quantity, Long purchaseAmount, Long valuationAmt,
                       Long valuationPl, BigDecimal earningsRate) {
        this.quantity = quantity;
        this.purchaseAmount = purchaseAmount;
        this.valuationAmt = valuationAmt;
        this.valuationPl = valuationPl;
        this.earningsRate = earningsRate;
        this.updatedAt = LocalDateTime.now();
    }
}
