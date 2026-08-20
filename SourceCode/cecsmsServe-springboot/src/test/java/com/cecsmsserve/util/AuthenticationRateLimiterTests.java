package com.cecsmsserve.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationRateLimiterTests {

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
