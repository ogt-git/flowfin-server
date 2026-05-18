package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
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
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "broker_code", length = 20)
    private String brokerCode;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "account_no", length = 50, unique = true)
    private String accountNo;

    @Column(name = "total_asset")
    private Long totalAsset;

    @Column(name = "deposit_received")
    private Long depositReceived;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static AssetAccount create(Long userId, String brokerCode, String accountNo,
                                       Long totalAsset, Long depositReceived) {
        AssetAccount account = new AssetAccount();
        account.userId = userId;
        account.brokerCode = brokerCode;
        account.accountNo = accountNo;
        account.totalAsset = totalAsset;
        account.depositReceived = depositReceived;
        account.updatedAt = LocalDateTime.now();
        return account;
    }

    public void updateAsset(Long totalAsset, Long depositReceived) {
        this.totalAsset = totalAsset;
        this.depositReceived = depositReceived;
        this.updatedAt = LocalDateTime.now();
    }
}
