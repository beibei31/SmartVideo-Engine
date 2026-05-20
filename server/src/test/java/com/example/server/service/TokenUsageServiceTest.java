package com.example.server.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenUsageServiceTest {

    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final TokenUsageService tokenUsageService = new TokenUsageService(redisTemplate, 50000);

    @Test
    void recordUsageIncrementsTokensAndSetsDailyTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("user:token:usage:7", 1234L)).thenReturn(1234L);
        when(redisTemplate.getExpire("user:token:usage:7")).thenReturn(-1L);

        tokenUsageService.recordUsage(7L, 1234);

        verify(valueOperations).increment("user:token:usage:7", 1234L);
        verify(redisTemplate).expire("user:token:usage:7", Duration.ofHours(24));
    }

    @Test
    void allowsRequestUntilDailyQuotaIsReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:token:usage:7")).thenReturn("49999");

        assertTrue(tokenUsageService.hasQuota(7L));
    }

    @Test
    void rejectsRequestAfterDailyQuotaIsExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:token:usage:7")).thenReturn("50001");

        assertFalse(tokenUsageService.hasQuota(7L));
    }
}
