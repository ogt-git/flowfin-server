package com.project.flowfinserver.domain;

import com.project.flowfinserver.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.*;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    @Convert(converter = AesEncryptConverter.class)
    private String email;

    // 이메일 조회용 SHA-256 해시 (AES는 매번 다른 암호문 생성 → WHERE 절 불가)
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 512)
    @Convert(converter = AesEncryptConverter.class)
    private String name;
  
    private String connectedId;  // CODEF Connected Account ID

    @Builder
    public User(String email, String password, String name, Provider provider, String connectedId) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.provider = provider;
        this.connectedId = connectedId;
    }

    public void updateConnectedId(String connectedId) {
        this.connectedId = connectedId;
    }
}
