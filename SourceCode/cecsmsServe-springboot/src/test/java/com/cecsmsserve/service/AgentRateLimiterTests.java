package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
}
