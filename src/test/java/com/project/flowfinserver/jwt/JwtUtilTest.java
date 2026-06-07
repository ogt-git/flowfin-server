package com.project.flowfinserver.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-ok";
    private static final String TEST_EMAIL = "user@flowfin.test";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 1_800_000L, 86_400_000L);
    }

    @Test
    @DisplayName("generateRefreshToken — 비어있지 않은 토큰 반환")
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateRefreshToken(TEST_EMAIL);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("getEmail — 생성된 토큰에서 이메일 파싱 성공")
    void getEmail_extractsCorrectEmail() {
        String token = jwtUtil.generateRefreshToken(TEST_EMAIL);
        assertThat(jwtUtil.getEmail(token)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("isValid — 유효한 토큰: true")
    void isValid_validToken_returnsTrue() {
        String token = jwtUtil.generateRefreshToken(TEST_EMAIL);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid — 만료된 토큰: false")
    void isValid_expiredToken_returnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(TEST_EMAIL)
                .issuedAt(new Date(0))
                .expiration(new Date(1))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.isValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("isValid — 다른 키로 서명된 토큰: false")
    void isValid_differentKeyToken_returnsFalse() {
        SecretKey differentKey = Keys.hmacShaKeyFor(
                "different-secret-key-for-test-that-is-256-bits-long!!".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject(TEST_EMAIL)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(differentKey)
                .compact();

        assertThat(jwtUtil.isValid(foreignToken)).isFalse();
    }
}
