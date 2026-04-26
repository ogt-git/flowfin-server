package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;  // 소셜 로그인은 null

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

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
