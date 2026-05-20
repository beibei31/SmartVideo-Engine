package com.example.server.service;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenUsageService {

    private static final String KEY_PREFIX = "user:token:usage:";
    private static final Duration DAILY_TTL = Duration.ofHours(24);

    private final RedissonClient redissonClient;
    private final long dailyQuota;

    public TokenUsageService(RedissonClient redissonClient,
                             @Value("${ai.token.daily-quota:50000}") long dailyQuota) {
        this.redissonClient = redissonClient;
        this.dailyQuota = dailyQuota;
    }

    public void recordUsage(Long userId, int totalTokens) {
        if (userId == null || totalTokens <= 0) {
            return;
        }

        String key = buildKey(userId);
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.addAndGet(totalTokens);
        counter.expire(DAILY_TTL);
    }

    public boolean hasQuota(Long userId) {
        if (userId == null) {
            return true;
        }

        RAtomicLong counter = redissonClient.getAtomicLong(buildKey(userId));
        if (!counter.isExists()) {
            return true;
        }

        return counter.get() <= dailyQuota;
    }

    public String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
