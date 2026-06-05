package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.*;
import com.project.flowfinserver.service.AuthService;
import com.project.flowfinserver.service.EmailVerificationService;
import com.project.flowfinserver.service.PasswordResetService;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.loginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshToken = extractRefreshTokenCookie(request);
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, result.newRefreshToken());
        return ResponseEntity.ok(new TokenResponse(result.newAccessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        authService.logout((Long) authentication.getPrincipal());
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new RuntimeException("리프레시 토큰이 없습니다.");
        }
        for (Cookie cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new RuntimeException("리프레시 토큰이 없습니다.");
    }

    @PostMapping("/email-verify/request")
    public ResponseEntity<String> requestEmailVerify(@Valid @RequestBody PasswordResetRequestDto request) {
        emailVerificationService.sendOtp(request.getEmail());
        return ResponseEntity.ok("인증코드가 이메일로 발송되었습니다.");
    }

    @PostMapping("/email-verify/confirm")
    public ResponseEntity<Map<String, String>> confirmEmailVerify(@Valid @RequestBody EmailVerifyConfirmDto request) {
        String token = emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(Map.of("verificationToken", token));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<String> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request) {
        passwordResetService.sendOtp(request.getEmail());
        return ResponseEntity.ok("인증코드가 이메일로 발송되었습니다.");
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<String> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto request) {
        passwordResetService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
    }
}
