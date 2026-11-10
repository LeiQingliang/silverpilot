package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRateLimiterTests {

    @Test
    void limitsEachUserWithinAFixedMinuteWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AgentRateLimiter limiter = new AgentRateLimiter(2, clock);

        assertTrue(limiter.acquire(17).allowed());
        assertTrue(limiter.acquire(17).allowed());
        assertFalse(limiter.acquire(17).allowed());
        assertTrue(limiter.acquire(18).allowed());
    }

    @Test
    void configuredRedisMissingOrFailingDoesNotPermitLocalFallback() {
        Clock clock = Clock.systemUTC();
        AgentRateLimiter missing = new AgentRateLimiter(2, clock, null, true);
        assertFalse(missing.acquire(17).allowed());
        StringRedisTemplate failed = new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                throw new IllegalStateException("connection unavailable");
            }
        };
        AgentRateLimiter limiter = new AgentRateLimiter(2, clock, failed, true);
        assertFalse(limiter.acquire(17).allowed());
        assertFalse(limiter.acquire(17).allowed());
        assertFalse(limiter.status().distributed());
    }

    @Test
    void explicitLocalLimiterRejectsNewIdentitiesAtCapacityAndReclaimsExpiredWindows() {
        var clock = new AiResourceGuardTests.MutableClock("2026-11-10T00:00:00Z");
        AgentRateLimiter limiter = new AgentRateLimiter(2, clock);
        for (int i = 1; i <= 4096; i++) assertTrue(limiter.acquire(i).allowed());
        assertFalse(limiter.acquire(4097).allowed());
        assertTrue(limiter.acquire(1).allowed());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.acquire(4097).allowed());
    }
}
