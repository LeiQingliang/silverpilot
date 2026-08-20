package com.cecsmsserve.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTests {

    @Test
    void localChallengeIsCaseInsensitiveOneTimeAndExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        CaptchaService service = new CaptchaService(Duration.ofMinutes(5), 10, clock, null, false);

        assertTrue(service.store("challenge-1", "Ab7Z"));
        assertTrue(service.consumeMatches("challenge-1", " ab7z "));
        assertFalse(service.consumeMatches("challenge-1", "AB7Z"));

        assertTrue(service.store("challenge-2", "K9PM"));
        clock.advance(Duration.ofMinutes(6));
        assertFalse(service.consumeMatches("challenge-2", "K9PM"));
    }

    @Test
    void localFallbackIsBoundedButAllowsReplacingTheSameChallenge() {
        CaptchaService service = new CaptchaService(
                Duration.ofMinutes(5), 1,
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                null, false);

        assertTrue(service.store("challenge-1", "AAAA"));
        assertFalse(service.store("challenge-2", "BBBB"));
        assertTrue(service.store("challenge-1", "CCCC"));
        assertFalse(service.consumeMatches("challenge-1", "AAAA"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisChallengeUsesTtlAndAtomicGetDelete() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("ABCD");
        CaptchaService service = new CaptchaService(
                Duration.ofMinutes(5), 10, Clock.systemUTC(), redis, true);

        assertTrue(service.store("challenge-1", "abcd"));
        assertTrue(service.consumeMatches("challenge-1", "ABCD"));

        verify(values).set(anyString(), anyString(), any(Duration.class));
        verify(values).getAndDelete(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureFallsBackToBoundedLocalStorage() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        CaptchaService service = new CaptchaService(
                Duration.ofMinutes(5), 10, Clock.systemUTC(), redis, true);

        assertTrue(service.store("challenge-1", "ABCD"));
        assertTrue(service.consumeMatches("challenge-1", "abcd"));
        assertFalse(service.consumeMatches("challenge-1", "abcd"));
    }

    @Test
    void rejectsMalformedKeysAndBlankCodes() {
        CaptchaService service = new CaptchaService(
                Duration.ofMinutes(5), 10, Clock.systemUTC(), null, false);

        assertFalse(service.isValidKey("../unsafe"));
        assertFalse(service.store("../unsafe", "ABCD"));
        assertFalse(service.store("safe-key", " "));
        assertFalse(service.consumeMatches("safe-key", null));

        assertTrue(service.store("blank-attempt", "ABCD"));
        assertFalse(service.consumeMatches("blank-attempt", " "));
        assertFalse(service.consumeMatches("blank-attempt", "ABCD"));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
