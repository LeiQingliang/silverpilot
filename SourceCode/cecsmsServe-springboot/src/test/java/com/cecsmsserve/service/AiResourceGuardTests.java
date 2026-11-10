package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiResourceGuardTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Object> BODY = Map.of("model", "test", "max_tokens", 20,
            "messages", List.of(Map.of("role", "user", "content", "hello")));
    private final MutableClock clock = new MutableClock("2026-11-10T00:00:00Z");

    @Test
    void paidCallRequiresAuthenticatedRequestLeaseAndValidSource() {
        AiResourceGuard guard = guard(defaults(), new LocalAiQuotaStore(4096));
        assertRejected(503, () -> guard.reserveModelCall("a", "test", BODY));
        assertRejected(401, () -> guard.acquireRequest(null, "127.0.0.1"));
        assertRejected(400, () -> guard.acquireRequest(17, "forwarded.example"));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            assertDoesNotThrow(() -> guard.reserveModelCall("a", "test", BODY));
        }
        assertRejected(503, () -> guard.reserveModelCall("a", "test", BODY));
    }

    @Test
    void concurrentAdmissionAcrossInstancesNeverExceedsGlobalSlots() throws Exception {
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        int contenders = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(contenders);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (int i = 0; i < contenders; i++) {
                final int id = i + 1;
                executor.submit(() -> {
                    AiResourceGuard guard = guard(defaults(), store);
                    try {
                        start.await();
                        try (var ignored = guard.acquireRequest(id, "127.0.0." + id)) {
                            admitted.incrementAndGet();
                            attempted.countDown();
                            release.await();
                        }
                    } catch (AiResourceGuard.Rejected ex) {
                        rejected.incrementAndGet();
                        attempted.countDown();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            try {
                assertTrue(attempted.await(10, TimeUnit.SECONDS));
                assertEquals(2, admitted.get());
                assertEquals(contenders - 2, rejected.get());
            } finally {
                release.countDown();
            }
        }
        try (var ignored = guard(defaults(), store).acquireRequest(100, "127.0.0.100")) {
            assertEquals(2, admitted.get());
        }
    }

    @Test
    void failedConcurrencyAdmissionDoesNotSpendAnyRequestQuota() {
        var limits = limits(1, 1, 2, 20, 1, 6, 200, 20, 40, 200, 500_000);
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        AiResourceGuard first = guard(limits, store);
        AiResourceGuard second = guard(limits, store);
        var lease = first.acquireRequest(17, "127.0.0.1");
        assertRejected(429, () -> second.acquireRequest(18, "127.0.0.2"));
        lease.close();
        lease.close();
        try (var ignored = second.acquireRequest(18, "127.0.0.2")) {
            assertRejected(429, () -> first.acquireRequest(19, "127.0.0.3"));
        }
        assertRejected(429, () -> second.acquireRequest(18, "127.0.0.2"));
    }

    @Test
    void sameUserCannotAcquireConcurrentRequestsFromDifferentAddresses() {
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        try (var ignored = guard(defaults(), store).acquireRequest(17, "127.0.0.1")) {
            assertRejected(429, () -> guard(defaults(), store).acquireRequest(17, "127.0.0.2"));
        }
    }

    @Test
    void rotatingAccountsOrIpv6SuffixesCannotBypassSourceQuota() {
        var limits = limits(2, 1, 2, 20, 3, 1, 200, 20, 40, 200, 500_000);
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        try (var ignored = guard(limits, store).acquireRequest(17, "2001:db8:1:2::1")) { }
        assertRejected(429, () -> guard(limits, store).acquireRequest(18, "2001:0db8:0001:0002::abcd"));
        try (var ignored = guard(limits, store).acquireRequest(18, "2001:db8:1:3::1")) { }
    }

    @Test
    void globalRequestQuotaAppliesAcrossUsersAndSources() {
        var limits = limits(2, 1, 2, 2, 3, 6, 200, 20, 40, 200, 500_000);
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        for (int id = 1; id <= 2; id++) {
            try (var ignored = guard(limits, store).acquireRequest(id, "127.0.0." + id)) { }
        }
        assertRejected(429, () -> guard(limits, store).acquireRequest(3, "127.0.0.3"));
    }

    @Test
    void dailyQuotaResetsAtShanghaiMidnightAndNotAtEachMinute() {
        clock.set("2026-11-10T15:58:00Z");
        var limits = limits(2, 1, 2, 20, 3, 6, 1, 20, 40, 200, 500_000);
        AiResourceGuard guard = guard(limits, new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) { }
        clock.advance(Duration.ofMinutes(1));
        assertRejected(429, () -> guard.acquireRequest(18, "127.0.0.2"));
        clock.advance(Duration.ofMinutes(1));
        try (var ignored = guard.acquireRequest(18, "127.0.0.2")) { }
    }

    @Test
    void globalModelBudgetIncludesEveryProviderAndUnsuccessfulAttempt() {
        AiResourceGuard guard = guard(limits(2, 1, 2, 20, 3, 6, 200, 20, 40, 2, 500_000), new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            guard.reserveModelCall("deepseek", "one", BODY); // failed/unknown response: no refund
            guard.reserveModelCall("longcat", "two", BODY);
            assertRejected(429, () -> guard.reserveModelCall("doubao", "three", BODY));
        }
    }

    @Test
    void failedTokenReservationDoesNotConsumeTheCallCounter() {
        AiResourceGuard guard = guard(limits(2, 1, 2, 20, 3, 6, 200, 20, 40, 1, 2000), new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            assertRejected(429, () -> guard.reserveModelCall("a", "test", Map.of("max_tokens", 20, "content", "x".repeat(3000))));
            assertDoesNotThrow(() -> guard.reserveModelCall("a", "test", BODY));
            assertRejected(429, () -> guard.reserveModelCall("a", "test", BODY));
        }
    }

    @Test
    void reportedUsageAboveReservationIsChargedExactlyOnceAndBlocksFurtherCalls() {
        AiResourceGuard guard = guard(limits(2, 1, 2, 20, 3, 6, 200, 20, 40, 200, 4000), new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            var reservation = guard.reserveModelCall("a", "test", BODY);
            reservation.recordUsage(2500);
            reservation.recordUsage(2500);
            guard.reserveModelCall("a", "test", BODY); // would fail if excess charged twice
            assertRejected(429, () -> guard.reserveModelCall("a", "test", BODY));
        }
    }

    @Test
    void hardPayloadAndOutputLimitsRejectBeforeSpendingAnyBudget() {
        AiResourceGuard guard = guard(limits(2, 1, 2, 20, 3, 6, 200, 20, 40, 1, 500_000), new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            assertRejected(413, () -> guard.reserveModelCall("a", "test", Map.of("max_tokens", 20, "content", "汉".repeat(25000))));
            assertRejected(400, () -> guard.reserveModelCall("a", "test", Map.of("max_tokens", -1)));
            assertDoesNotThrow(() -> guard.reserveModelCall("a", "test", BODY));
        }
    }

    @Test
    void deadlineBlocksFurtherPaidAttemptsEvenWhenLeaseHasNotExpired() {
        AiResourceGuard guard = guard(defaults(), new LocalAiQuotaStore(4096));
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            clock.advance(Duration.ofSeconds(91));
            assertRejected(429, () -> guard.reserveModelCall("a", "test", BODY));
        }
    }

    @Test
    void expiredLeaseIsRecoveredAndItsLateReleaseCannotRemoveANewerLease() {
        var limits = limits(1, 1, 2, 20, 3, 6, 200, 20, 40, 200, 500_000);
        LocalAiQuotaStore store = new LocalAiQuotaStore(4096);
        AiResourceGuard old = guard(limits, store);
        var abandoned = old.acquireRequest(17, "127.0.0.1");
        clock.advance(Duration.ofMinutes(6));
        try (var replacement = guard(limits, store).acquireRequest(17, "127.0.0.1")) {
            abandoned.close();
            assertRejected(429, () -> guard(limits, store).acquireRequest(18, "127.0.0.2"));
        }
    }

    @Test
    void explicitLocalModeHasABoundedMapAndReclaimsExpiredState() {
        LocalAiQuotaStore store = new LocalAiQuotaStore(16);
        var limits = limits(2, 1, 2, 1000, 1000, 1000, 1000, 1000, 1000, 200, 500_000);
        for (int id = 1; id <= 100; id++) {
            try (var ignored = guard(limits, store).acquireRequest(id, "127.0.0." + id)) { }
            catch (AiResourceGuard.Rejected ex) { assertEquals(429, ex.getHttpStatus()); }
        }
        assertTrue(store.size(clock.millis()) <= 16);
        clock.advance(Duration.ofDays(2));
        assertEquals(0, store.size(clock.millis()));
        try (var ignored = guard(limits, store).acquireRequest(1, "127.0.0.1")) { }
    }

    @Test
    void configuredRedisFailureNeverFallsBackToLocalQuota() {
        StringRedisTemplate unavailable = new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                throw new IllegalStateException("connection unavailable");
            }
        };
        AiResourceGuard guard = guard(defaults(), new RedisAiQuotaStore(unavailable));
        for (int i = 0; i < 3; i++) assertRejected(503, () -> guard.acquireRequest(17, "127.0.0.1"));
    }

    @Test
    void configuredRedisMissingBeanIsClosedInsteadOfEnablingLocalMode() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        AiResourceGuard guard = new AiResourceGuard(new MockEnvironment().withProperty("agent.redis.enabled", "true"), provider, JSON);
        assertRejected(503, () -> guard.acquireRequest(17, "127.0.0.1"));
    }

    @Test
    void nullRedisScriptResultFailsClosed() {
        StringRedisTemplate unavailable = new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) { return null; }
        };
        assertRejected(503, () -> guard(defaults(), new RedisAiQuotaStore(unavailable)).acquireRequest(17, "127.0.0.1"));
    }

    @Test
    void redisFailureAfterAdmissionPreventsModelReservationAndReleaseIsIdempotent() {
        AtomicInteger releases = new AtomicInteger();
        AiQuotaStore failing = new AiQuotaStore() {
            public boolean admit(List<Counter> c, List<Slot> s, String t, long n, long e, boolean acquire) {
                if (!acquire) throw new IllegalStateException("Redis stopped after admission");
                return true;
            }
            public void release(List<Slot> s, String t) {
                releases.incrementAndGet();
                throw new IllegalStateException("Redis stopped");
            }
            public void charge(List<Counter> c, long x, long n) { throw new AssertionError("No call was admitted"); }
        };
        AiResourceGuard guard = guard(defaults(), failing);
        var lease = guard.acquireRequest(17, "127.0.0.1");
        assertRejected(503, () -> guard.reserveModelCall("a", "test", BODY));
        assertDoesNotThrow(lease::close);
        assertDoesNotThrow(lease::close);
        assertEquals(1, releases.get());
        assertRejected(503, () -> guard.reserveModelCall("a", "test", BODY));
    }

    @Test
    void failedExcessAccountingClosesSubsequentAdmission() {
        AiQuotaStore broken = new AiQuotaStore() {
            public boolean admit(List<Counter> c, List<Slot> s, String t, long n, long e, boolean a) { return true; }
            public void release(List<Slot> s, String t) { }
            public void charge(List<Counter> c, long x, long n) { throw new IllegalStateException("unavailable"); }
        };
        AiResourceGuard guard = guard(defaults(), broken);
        try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
            var reservation = guard.reserveModelCall("a", "test", BODY);
            assertRejected(503, () -> reservation.recordUsage(100_000));
        }
        assertRejected(503, () -> guard.acquireRequest(18, "127.0.0.2"));
    }

    private AiResourceGuard guard(AiResourceGuard.Limits limits, AiQuotaStore store) {
        return new AiResourceGuard(limits, clock, store, JSON);
    }

    private static AiResourceGuard.Limits defaults() { return AiResourceGuard.Limits.defaults(); }

    private static AiResourceGuard.Limits limits(int gc, int uc, int ic, int gm, int um, int im,
                                                int gd, int ud, int id, long calls, long tokens) {
        return new AiResourceGuard.Limits(gc, uc, ic, gm, um, im, gd, ud, id, calls, tokens,
                65536, Duration.ofMinutes(5), Duration.ofSeconds(90));
    }

    private static void assertRejected(int status, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(AiResourceGuard.Rejected.class, action);
        assertEquals(status, ex.getHttpStatus());
        assertTrue(ex.retryAfterSeconds() > 0);
    }

    static final class MutableClock extends Clock {
        private final AtomicLong millis;
        MutableClock(String time) { millis = new AtomicLong(Instant.parse(time).toEpochMilli()); }
        void set(String time) { millis.set(Instant.parse(time).toEpochMilli()); }
        void advance(Duration duration) { millis.addAndGet(duration.toMillis()); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        @Override public long millis() { return millis.get(); }
    }
}
