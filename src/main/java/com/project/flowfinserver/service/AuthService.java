package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.*;
import com.project.flowfinserver.jwt.JwtUtil;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AesEncryptionUtil encryptionUtil;
    private final RedisTokenService redisTokenService;

    public void signup(SignupRequest request) {
        String emailHash = encryptionUtil.hash(request.getEmail());
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = User.create(
                request.getEmail(),
                emailHash,
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getRiskType()
        );

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        String emailHash = encryptionUtil.hash(request.getEmail());
        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        redisTokenService.saveRefreshToken(user.getId(), refreshToken);

        return new LoginResponse(user.getId(), accessToken, refreshToken, user.getName());
    }

    // RTR: 리프레시 토큰 검증 → 새 토큰 쌍 발급, 기존 토큰 즉시 삭제
    public TokenResponse refresh(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken)) {
            throw new RuntimeException("유효하지 않은 리프레시 토큰입니다.");
        }

        String email     = jwtUtil.getEmail(refreshToken);
        String emailHash = encryptionUtil.hash(email);
        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다."));

        String storedToken = redisTokenService.getRefreshToken(user.getId());

        // 토큰 불일치 → 탈취 의심, Redis 토큰 즉시 삭제 후 차단
        if (!refreshToken.equals(storedToken)) {
            redisTokenService.deleteRefreshToken(user.getId());
            throw new RuntimeException("리프레시 토큰이 일치하지 않습니다. 다시 로그인해주세요.");
        }

        // 기존 토큰 삭제 후 새 토큰 쌍 발급 (RTR)
        redisTokenService.deleteRefreshToken(user.getId());

        String newAccessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        redisTokenService.saveRefreshToken(user.getId(), newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        redisTokenService.deleteRefreshToken(userId);
    }
}
