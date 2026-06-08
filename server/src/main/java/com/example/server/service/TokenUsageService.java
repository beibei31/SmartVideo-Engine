package com.example.server.service;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenUsageService {

    private static final String KEY_PREFIX = "user:token:usage:";
    private static final Duration DAILY_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(TokenUsageService.class);

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

        try {
            String key = buildKey(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long currentUsage = counter.addAndGet(totalTokens);
            if (currentUsage == totalTokens) {
                counter.expire(DAILY_TTL);
            }
        } catch (Exception e) {
            // Redis 不可用 → 记账失败，但分析结果的 Token 用量已从 API 响应中拿到
            // 可通过日志异步对账，Redis 恢复后补录
            log.warn("Redis 不可用，Token 记账失败，待 Redis 恢复后对账: userId={}, tokens={}", userId, totalTokens, e);
        }
    }

    public boolean hasQuota(Long userId) {
        if (userId == null) {
            return true;
        }

        try {
            RAtomicLong counter = redissonClient.getAtomicLong(buildKey(userId));
            if (!counter.isExists()) {
                return true;
            }
            return counter.get() <= dailyQuota;
        } catch (Exception e) {
            // Redis 不可用 → 放行请求，保核心业务不断供
            // 容忍极小概率的额度超发，等 Redis 恢复后可通过 AOF 对账
            log.warn("Redis 不可用，跳过 Token 额度检查，直接放行: userId={}", userId, e);
            return true;
        }
    }

    public long getDailyQuota() {
        return dailyQuota;
    }

    public String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
