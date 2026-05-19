package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.LoginRequest;
import com.project.flowfinserver.dto.LoginResponse;
import com.project.flowfinserver.dto.SignupRequest;
import com.project.flowfinserver.jwt.JwtUtil;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.service.RedisTokenService;
import com.project.flowfinserver.util.AesEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AesEncryptionUtil encryptionUtil;
    @Mock RedisTokenService redisTokenService;

    @InjectMocks
    AuthService authService;

    private SignupRequest buildSignupRequest(String email, String password, String name) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "name", name);
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    @Test
    @DisplayName("signup — 신규 이메일: 해시 검증 후 저장")
    void signup_success() {
        SignupRequest request = buildSignupRequest("test@flowfin.test", "password123!", "홍길동");
        given(encryptionUtil.hash("test@flowfin.test")).willReturn("hashed-email");
        given(userRepository.existsByEmailHash("hashed-email")).willReturn(false);

        authService.signup(request);

        then(userRepository).should(times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("signup — 이미 존재하는 이메일: RuntimeException 발생")
    void signup_duplicateEmail_throwsException() {
        SignupRequest request = buildSignupRequest("dup@flowfin.test", "pass", "이중복");
        given(encryptionUtil.hash("dup@flowfin.test")).willReturn("dup-hash");
        given(userRepository.existsByEmailHash("dup-hash")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미 존재하는 이메일");

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("login — 정상 자격증명: userId, token, name 반환")
    void login_success() {
        LoginRequest request = buildLoginRequest("test@flowfin.test", "raw-password");

        User storedUser = User.create("test@flowfin.test", "hashed-email", "encoded-pw", "홍길동");
        ReflectionTestUtils.setField(storedUser, "id", 1L);

        given(encryptionUtil.hash("test@flowfin.test")).willReturn("hashed-email");
        given(userRepository.findByEmailHash("hashed-email")).willReturn(Optional.of(storedUser));
        given(passwordEncoder.matches("raw-password", "encoded-pw")).willReturn(true);
        given(jwtUtil.generateAccessToken(eq("test@flowfin.test"), anyLong())).willReturn("access-token");
        given(jwtUtil.generateRefreshToken("test@flowfin.test")).willReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("login — 존재하지 않는 이메일: RuntimeException 발생")
    void login_emailNotFound_throwsException() {
        LoginRequest request = buildLoginRequest("unknown@flowfin.test", "pass");
        given(encryptionUtil.hash("unknown@flowfin.test")).willReturn("unknown-hash");
        given(userRepository.findByEmailHash("unknown-hash")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("존재하지 않는 이메일");
    }

    @Test
    @DisplayName("login — 비밀번호 불일치: RuntimeException 발생")
    void login_wrongPassword_throwsException() {
        LoginRequest request = buildLoginRequest("test@flowfin.test", "wrong-pw");

        User storedUser = User.create("test@flowfin.test", "hashed-email", "encoded-pw", "홍길동");
        given(encryptionUtil.hash("test@flowfin.test")).willReturn("hashed-email");
        given(userRepository.findByEmailHash("hashed-email")).willReturn(Optional.of(storedUser));
        given(passwordEncoder.matches("wrong-pw", "encoded-pw")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");
    }
}
