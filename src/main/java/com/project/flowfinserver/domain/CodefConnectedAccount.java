package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "codef_connected_account")
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

    @Column(name = "organization_code", nullable = false, length = 20)
    private String organizationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "is_active")
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CodefConnectedAccount(Long userId, String connectedId,
                                  String organizationCode, AccountType accountType) {
        this.userId = userId;
        this.connectedId = connectedId;
        this.organizationCode = organizationCode;
        this.accountType = accountType;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
