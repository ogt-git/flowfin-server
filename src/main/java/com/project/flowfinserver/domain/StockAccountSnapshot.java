package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "stock_account_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_snapshot",
                columnNames = {"user_id", "snapshot_date", "broker_name"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAccountSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "broker_name", length = 50)
    private String brokerName;

    @Column(name = "total_eval_amount")
    private Long totalEvalAmount;

    @Column(name = "total_purchase_amount")
    private Long totalPurchaseAmount;

    @Column(name = "profit_loss")
    private Long profitLoss;

    @Column(name = "deposit_received")
    private Long depositReceived; // 예수금 (resDepositReceived)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "JSON")
    private Map<String, Object> rawData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public StockAccountSnapshot(Long userId, LocalDate snapshotDate, String brokerName,
                                 Long totalEvalAmount, Long totalPurchaseAmount,
                                 Long profitLoss, Long depositReceived, Map<String, Object> rawData) {
        this.userId = userId;
        this.snapshotDate = snapshotDate;
        this.brokerName = brokerName;
        this.totalEvalAmount = totalEvalAmount;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.profitLoss = profitLoss;
        this.depositReceived = depositReceived;
        this.rawData = rawData;
    }
}
