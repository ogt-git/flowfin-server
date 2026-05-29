package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "codef_connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodefConnectedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "connected_id", nullable = false)
    private String connectedId;

    @Column(name = "organization_code", nullable = false, length = 100)
    private String organizationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "account_password", length = 255)
    private String accountPassword;

    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static CodefConnectedAccount create(Long userId, String connectedId,
                                                String organizationCode, AccountType accountType) {
        CodefConnectedAccount conn = new CodefConnectedAccount();
        conn.userId = userId;
        conn.connectedId = connectedId;
        conn.organizationCode = organizationCode;
        conn.accountType = accountType;
        conn.isActive = true;
        return conn;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void reactivate(String newConnectedId) {
        this.connectedId = newConnectedId;
        this.isActive = true;
    }

    public void updateAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void updateAccountPassword(String accountPassword) {
        this.accountPassword = accountPassword;
    }
}
