package com.example.server.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenUsageServiceTest {

    private final RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
    private final RAtomicLong counter = Mockito.mock(RAtomicLong.class);
    private final TokenUsageService tokenUsageService = new TokenUsageService(redissonClient, 50000);

    @Test
    void recordUsageIncrementsTokensAndSetsDailyTtl() {
        when(redissonClient.getAtomicLong("user:token:usage:7")).thenReturn(counter);
        when(counter.addAndGet(1234)).thenReturn(1234L);

        tokenUsageService.recordUsage(7L, 1234);

        verify(counter).addAndGet(1234);
    }

    @Test
    void allowsRequestUntilDailyQuotaIsReached() {
        when(redissonClient.getAtomicLong("user:token:usage:7")).thenReturn(counter);
        when(counter.isExists()).thenReturn(true);
        when(counter.get()).thenReturn(49999L);

        assertTrue(tokenUsageService.hasQuota(7L));
    }

    @Test
    void rejectsRequestAfterDailyQuotaIsExceeded() {
        when(redissonClient.getAtomicLong("user:token:usage:7")).thenReturn(counter);
        when(counter.isExists()).thenReturn(true);
        when(counter.get()).thenReturn(50001L);

        assertFalse(tokenUsageService.hasQuota(7L));
    }
}
