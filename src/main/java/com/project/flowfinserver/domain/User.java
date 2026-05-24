package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import lombok.*;


@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = AesEncryptConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    // 이메일 조회용 SHA-256 해시 (AES는 매번 다른 암호문 생성 → WHERE 절 불가)
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    private String connectedId;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_expired_at")
    private LocalDateTime tokenExpiredAt;

    @Column(name = "risk_type", length = 50)
    private String riskType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    public static User create(String email, String emailHash, String password, String name, String riskType) {
        User user = new User();
        user.email = email;
        user.emailHash = emailHash;
        user.password = password;
        user.name = name;
        user.riskType = riskType;
        return user;
    }

    public void updateRefreshToken(String refreshToken, LocalDateTime tokenExpiredAt) {
        this.refreshToken = refreshToken;
        this.tokenExpiredAt = tokenExpiredAt;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
        this.tokenExpiredAt = null;
    }


    public void updateConnectedId(String connectedId) {
        this.connectedId = connectedId;
    }
}
