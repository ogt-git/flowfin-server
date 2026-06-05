package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.exception.TooManyRequestsException;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock AesEncryptionUtil encryptionUtil;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock JavaMailSender mailSender;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "fromAddress", "noreply@flowfin.test");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ==================== sendOtp ====================

    @Test
    @DisplayName("sendOtp — 가입된 이메일: OTP 저장 후 이메일 발송")
    void sendOtp_success() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(userRepository.existsByEmailHash("user-hash")).willReturn(true);
        given(redisTemplate.hasKey("otp:cooldown:user-hash")).willReturn(false);

        passwordResetService.sendOtp("user@flowfin.test");

        then(valueOps).should().set(eq("otp:code:user-hash"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        then(valueOps).should().set(eq("otp:cooldown:user-hash"), eq("1"), eq(15L), eq(TimeUnit.SECONDS));
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendOtp — 미가입 이메일: EntityNotFoundException 발생")
    void sendOtp_userNotFound_throwsException() {
        given(encryptionUtil.hash("unknown@flowfin.test")).willReturn("unknown-hash");
        given(userRepository.existsByEmailHash("unknown-hash")).willReturn(false);

        assertThatThrownBy(() -> passwordResetService.sendOtp("unknown@flowfin.test"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("등록되지 않은 이메일");

        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("sendOtp — 쿨다운 중: TooManyRequestsException 발생")
    void sendOtp_cooldown_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(userRepository.existsByEmailHash("user-hash")).willReturn(true);
        given(redisTemplate.hasKey("otp:cooldown:user-hash")).willReturn(true);

        assertThatThrownBy(() -> passwordResetService.sendOtp("user@flowfin.test"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15초에 한 번만");

        then(mailSender).shouldHaveNoInteractions();
    }

    // ==================== resetPassword ====================

    @Test
    @DisplayName("resetPassword — 올바른 OTP: 비밀번호 변경 후 OTP 삭제")
    void resetPassword_success() {
        User user = User.create("user@flowfin.test", "user-hash", "old-encoded-pw", "홍길동", null, "1.0");

        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("otp:code:user-hash")).willReturn("123456");
        given(userRepository.findByEmailHash("user-hash")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPass1!")).willReturn("new-encoded-pw");

        passwordResetService.resetPassword("user@flowfin.test", "123456", "newPass1!");

        then(redisTemplate).should().delete("otp:code:user-hash");
    }

    @Test
    @DisplayName("resetPassword — OTP 만료: IllegalStateException 발생")
    void resetPassword_expiredOtp_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("otp:code:user-hash")).willReturn(null);

        assertThatThrownBy(() -> passwordResetService.resetPassword("user@flowfin.test", "123456", "newPass1!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증코드가 만료");
    }

    @Test
    @DisplayName("resetPassword — OTP 불일치: IllegalArgumentException 발생")
    void resetPassword_wrongOtp_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("otp:code:user-hash")).willReturn("123456");

        assertThatThrownBy(() -> passwordResetService.resetPassword("user@flowfin.test", "999999", "newPass1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("인증코드가 올바르지 않습니다");
    }

    @Test
    @DisplayName("resetPassword — OTP 일치하나 사용자 없음: EntityNotFoundException 발생")
    void resetPassword_userNotFound_throwsException() {
        given(encryptionUtil.hash("ghost@flowfin.test")).willReturn("ghost-hash");
        given(valueOps.get("otp:code:ghost-hash")).willReturn("123456");
        given(userRepository.findByEmailHash("ghost-hash")).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("ghost@flowfin.test", "123456", "newPass1!"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
