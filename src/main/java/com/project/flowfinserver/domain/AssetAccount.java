package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "broker_code", length = 20)
    private String brokerCode;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "account_no", length = 50)
    private String accountNo;

    @Column(name = "total_asset")
    private Long totalAsset;

    @Column(name = "deposit_received")
    private Long depositReceived;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public AssetAccount(Long userId, String brokerCode, String accountNo,
                        Long totalAsset, Long depositReceived) {
        this.userId = userId;
        this.brokerCode = brokerCode;
        this.accountNo = accountNo;
        this.totalAsset = totalAsset;
        this.depositReceived = depositReceived;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Long totalAsset, Long depositReceived) {
        this.totalAsset = totalAsset;
        this.depositReceived = depositReceived;
        this.updatedAt = LocalDateTime.now();
    }
}
