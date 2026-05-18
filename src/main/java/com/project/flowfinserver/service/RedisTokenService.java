package com.project.flowfinserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final long REFRESH_TTL_DAYS = 7;

    private final RedisTemplate<String, String> redisTemplate;

    public void saveRefreshToken(Long userId, String token) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                token,
                REFRESH_TTL_DAYS,
                TimeUnit.DAYS
        );
    }

    public String getRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }
}
