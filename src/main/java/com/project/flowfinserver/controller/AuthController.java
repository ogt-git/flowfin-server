package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.*;
import com.project.flowfinserver.service.AuthService;
import com.project.flowfinserver.service.EmailVerificationService;
import com.project.flowfinserver.service.PasswordResetService;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout((Long) authentication.getPrincipal());
        return ResponseEntity.ok().build();
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
