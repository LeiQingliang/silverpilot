package com.cecsmsserve.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthenticationRateLimiterTests {

    @Test
    void registrationReservesBothBudgetsAtomicallyUnderConcurrency() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AuthenticationRateLimiter sameIp = new AuthenticationRateLimiter(30, 8, clock, null, false, 3, 30, false);
        long accepted = IntStream.range(0, 100).parallel()
                .filter(index -> sameIp.acquireRegistration("203.0.113.1").allowed()).count();
        assertEquals(3, accepted);
        AuthenticationRateLimiter global = new AuthenticationRateLimiter(30, 8, clock, null, false, 3, 7, false);
        long globalAccepted = IntStream.range(0, 100).parallel()
                .filter(index -> global.acquireRegistration("source-" + index).allowed()).count();
        assertEquals(7, globalAccepted);
    }

    @Test
    void rejectedIpDoesNotSpendAnotherGlobalSlotAndCaptchaCleanupCannotResetRegistration() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(30, 8, clock, null, false, 1, 2, false);
        assertTrue(limiter.acquireRegistration("first").allowed());
        assertFalse(limiter.acquireRegistration("first").allowed());
        IntStream.range(0, 256).forEach(index -> limiter.acquireCaptcha("cleanup-source"));
        assertFalse(limiter.acquireRegistration("first").allowed());
        assertTrue(limiter.acquireRegistration("second").allowed());
        assertFalse(limiter.acquireRegistration("third").allowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registrationFailsClosedWhenRedisIsMissingOrUnavailable() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AuthenticationRateLimiter missing = new AuthenticationRateLimiter(30, 8, clock, null, false, 3, 30, true);
        assertFalse(missing.acquireRegistration("first").available());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("test Redis failure"));
        AuthenticationRateLimiter unavailable = new AuthenticationRateLimiter(30, 8, clock, redis, true, 3, 30, true);
        assertFalse(unavailable.acquireRegistration("first").available());
        assertFalse(unavailable.acquireRegistration("first").allowed());
    }

    @Test
    void boundsCaptchaAndLoginAttemptsWithoutKeepingRawIdentityKeys() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(2, 2, clock);

        assertTrue(limiter.acquireCaptcha("127.0.0.1").allowed());
        assertTrue(limiter.acquireCaptcha("127.0.0.1").allowed());
        assertFalse(limiter.acquireCaptcha("127.0.0.1").allowed());
        assertTrue(limiter.acquireLogin("127.0.0.1", "demo").allowed());
        assertTrue(limiter.acquireLogin("127.0.0.1", "demo").allowed());
        assertFalse(limiter.acquireLogin("127.0.0.1", "demo").allowed());
    }

    @Test
    void successfulLoginCanResetCurrentWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(1, 1, clock);
        assertTrue(limiter.acquireLogin("127.0.0.1", "demo").allowed());
        assertFalse(limiter.acquireLogin("127.0.0.1", "demo").allowed());
        limiter.resetLogin("127.0.0.1", "demo");
        assertTrue(limiter.acquireLogin("127.0.0.1", "demo").allowed());
    }
}
