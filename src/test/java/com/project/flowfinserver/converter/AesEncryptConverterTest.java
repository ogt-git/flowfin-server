package com.project.flowfinserver.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class AesEncryptConverterTest {

    private AesEncryptConverter converter;

    private static final String SECRET_KEY = "test-aes-32bytes-key-placeholder!!";

    @BeforeEach
    void setUp() {
        converter = new AesEncryptConverter();
        ReflectionTestUtils.setField(converter, "secretKey", SECRET_KEY);
    }

    @Test
    @DisplayName("암호화 후 복호화 → 원본 문자열 복원")
    void encrypt_decrypt_roundtrip() {
        String plain = "test-connected-id-12345";

        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("null 입력 → null 반환 (NPE 없음)")
    void nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("암호문은 평문과 달라야 한다")
    void ciphertext_differsFromPlaintext() {
        String plain = "account-number-001";

        String encrypted = converter.convertToDatabaseColumn(plain);

        assertThat(encrypted).isNotEqualTo(plain);
    }

    @Test
    @DisplayName("동일 평문도 매 호출마다 다른 암호문 생성 (random IV)")
    void sameInput_producesDifferentCiphertext() {
        String plain = "same-plain-text";

        String first  = converter.convertToDatabaseColumn(plain);
        String second = converter.convertToDatabaseColumn(plain);

        assertThat(first).isNotEqualTo(second);
    }
}
