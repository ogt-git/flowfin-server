package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.LoginRequest;
import com.project.flowfinserver.dto.LoginResponse;
import com.project.flowfinserver.dto.SignupRequest;
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

    public void signup(SignupRequest request) {
        String emailHash = encryptionUtil.hash(request.getEmail());
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = User.create(
                request.getEmail(),
                emailHash,
                passwordEncoder.encode(request.getPassword()),
                request.getName()
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

        String token = jwtUtil.generateToken(user.getEmail());
        return new LoginResponse(user.getId(), token, user.getName());
    }
}