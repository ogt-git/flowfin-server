package com.project.flowfinserver.service;

import com.project.flowfinserver.exception.DuplicateEmailException;
import com.project.flowfinserver.exception.TooManyRequestsException;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock UserRepository userRepository;
    @Mock AesEncryptionUtil encryptionUtil;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock JavaMailSender mailSender;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailVerificationService, "fromAddress", "noreply@flowfin.test");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ==================== sendOtp ====================

    @Test
    @DisplayName("sendOtp — 정상 요청: OTP 저장 후 이메일 발송")
    void sendOtp_success() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(userRepository.existsByEmailHash("user-hash")).willReturn(false);
        given(redisTemplate.hasKey("signup:cooldown:user-hash")).willReturn(false);

        emailVerificationService.sendOtp("user@flowfin.test");

        then(valueOps).should().set(eq("signup:otp:user-hash"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        then(valueOps).should().set(eq("signup:cooldown:user-hash"), eq("1"), eq(15L), eq(TimeUnit.SECONDS));
        then(mailSender).should().send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendOtp — 이미 가입된 이메일: DuplicateEmailException 발생")
    void sendOtp_duplicateEmail_throwsException() {
        given(encryptionUtil.hash("dup@flowfin.test")).willReturn("dup-hash");
        given(userRepository.existsByEmailHash("dup-hash")).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.sendOtp("dup@flowfin.test"))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("이미 사용 중인 이메일");

        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("sendOtp — 쿨다운 중: TooManyRequestsException 발생")
    void sendOtp_cooldown_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(userRepository.existsByEmailHash("user-hash")).willReturn(false);
        given(redisTemplate.hasKey("signup:cooldown:user-hash")).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.sendOtp("user@flowfin.test"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15초에 한 번만");

        then(mailSender).shouldHaveNoInteractions();
    }

    // ==================== verifyOtp ====================

    @Test
    @DisplayName("verifyOtp — OTP 일치: 검증 토큰 반환 및 OTP 삭제")
    void verifyOtp_success() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("signup:otp:user-hash")).willReturn("123456");

        String token = emailVerificationService.verifyOtp("user@flowfin.test", "123456");

        assertThat(token).isNotBlank();
        then(valueOps).should().set(eq("signup:verified:" + token), eq("user-hash"), eq(10L), eq(TimeUnit.MINUTES));
        then(redisTemplate).should().delete("signup:otp:user-hash");
    }

    @Test
    @DisplayName("verifyOtp — OTP 만료: IllegalStateException 발생")
    void verifyOtp_expired_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("signup:otp:user-hash")).willReturn(null);

        assertThatThrownBy(() -> emailVerificationService.verifyOtp("user@flowfin.test", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증코드가 만료");
    }

    @Test
    @DisplayName("verifyOtp — OTP 불일치: IllegalArgumentException 발생")
    void verifyOtp_wrongOtp_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("signup:otp:user-hash")).willReturn("123456");

        assertThatThrownBy(() -> emailVerificationService.verifyOtp("user@flowfin.test", "999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("인증코드가 올바르지 않습니다");
    }

    // ==================== validateAndConsume ====================

    @Test
    @DisplayName("validateAndConsume — 유효한 토큰: 검증 성공 후 토큰 삭제")
    void validateAndConsume_success() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("signup:verified:valid-token")).willReturn("user-hash");

        emailVerificationService.validateAndConsume("user@flowfin.test", "valid-token");

        then(redisTemplate).should().delete("signup:verified:valid-token");
    }

    @Test
    @DisplayName("validateAndConsume — 만료/불일치 토큰: IllegalStateException 발생")
    void validateAndConsume_invalidToken_throwsException() {
        given(encryptionUtil.hash("user@flowfin.test")).willReturn("user-hash");
        given(valueOps.get("signup:verified:invalid-token")).willReturn(null);

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("user@flowfin.test", "invalid-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일 인증이 완료되지 않았습니다");
    }

    @Test
    @DisplayName("validateAndConsume — 다른 이메일의 토큰: IllegalStateException 발생")
    void validateAndConsume_tokenEmailMismatch_throwsException() {
        given(encryptionUtil.hash("other@flowfin.test")).willReturn("other-hash");
        given(valueOps.get("signup:verified:valid-token")).willReturn("user-hash");

        assertThatThrownBy(() -> emailVerificationService.validateAndConsume("other@flowfin.test", "valid-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일 인증이 완료되지 않았습니다");
    }
}
