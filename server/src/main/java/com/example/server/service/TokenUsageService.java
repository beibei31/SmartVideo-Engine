package com.example.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenUsageService {

    private static final String KEY_PREFIX = "user:token:usage:";
    private static final Duration DAILY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final long dailyQuota;

    public TokenUsageService(StringRedisTemplate redisTemplate,
                             @Value("${ai.token.daily-quota:50000}") long dailyQuota) {
        this.redisTemplate = redisTemplate;
        this.dailyQuota = dailyQuota;
    }

    public void recordUsage(Long userId, int totalTokens) {
        if (userId == null || totalTokens <= 0) {
            return;
        }

        String key = buildKey(userId);
        redisTemplate.opsForValue().increment(key, totalTokens);
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(key, DAILY_TTL);
        }
    }

    public boolean hasQuota(Long userId) {
        if (userId == null) {
            return true;
        }

        String value = redisTemplate.opsForValue().get(buildKey(userId));
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            return Long.parseLong(value) <= dailyQuota;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    public String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
