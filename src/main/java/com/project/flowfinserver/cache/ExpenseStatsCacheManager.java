package com.project.flowfinserver.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.dto.expense.MonthlyStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// expense:stats:{userId}:{yyyyMM}  TTL: 3600초
// @Cacheable 미사용 — 키 패턴 직접 제어 + evict 호출 시점 명시 필요
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseStatsCacheManager {

    private static final String KEY_PREFIX = "expense:stats:";
    private static final long   TTL_SECONDS = 3600L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public MonthlyStatsResponse get(Long userId, String month) {
        String json = redisTemplate.opsForValue().get(buildKey(userId, month));
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, MonthlyStatsResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("[StatsCache] 역직렬화 실패 — 캐시 미스로 처리 userId={} month={}", userId, month);
            return null;
        }
    }

    public void set(Long userId, String month, MonthlyStatsResponse data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(buildKey(userId, month), json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("[StatsCache] 직렬화 실패 — 캐시 저장 생략 userId={} month={}", userId, month);
        }
    }

    /** 지출 데이터 변경 시 반드시 호출 (INSERT / 카테고리 수정 / is_excluded 변경) */
    public void evict(Long userId, String month) {
        redisTemplate.delete(buildKey(userId, month));
    }

    private String buildKey(Long userId, String month) {
        return KEY_PREFIX + userId + ":" + month;
    }
}
