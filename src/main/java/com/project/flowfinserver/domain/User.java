package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = AesEncryptConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_expired_at")
    private LocalDateTime tokenExpiredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static User create(String email, String emailHash, String password, String name) {
        User user = new User();
        user.email = email;
        user.emailHash = emailHash;
        user.password = password;
        user.name = name;
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
}
