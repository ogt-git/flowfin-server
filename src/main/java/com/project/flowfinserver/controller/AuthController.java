package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.*;
import com.project.flowfinserver.exception.AuthException;
import com.project.flowfinserver.jwt.JwtUtil;
import com.project.flowfinserver.service.AuthService;

import com.project.flowfinserver.service.EmailVerificationService;
import com.project.flowfinserver.service.PasswordResetService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site}")
    private String cookieSameSite;

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        addRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.loginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new AuthException("리프레시 토큰이 없습니다. 다시 로그인해주세요.");
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        TokenResponse tokenResponse = new TokenResponse(result.newAccessToken(), result.newRefreshToken());
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        authService.logout((Long) authentication.getPrincipal());
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(Duration.ofMillis(jwtUtil.getRefreshExpirationMs()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
