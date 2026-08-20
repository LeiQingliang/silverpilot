package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AgentRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<Integer, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong();
    private final AtomicLong redisFailureCounter = new AtomicLong();

    @Autowired
    public AgentRateLimiter(
            @Value("${agent.rate-limit.per-minute:12}") int requestsPerMinute,
            @Value("${agent.redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this(requestsPerMinute, Clock.systemUTC(),
                redisEnabled ? redisProvider.getIfAvailable() : null, redisEnabled);
    }

    AgentRateLimiter(int requestsPerMinute, Clock clock) {
        this(requestsPerMinute, clock, null, false);
    }

    AgentRateLimiter(
            int requestsPerMinute,
            Clock clock,
            StringRedisTemplate redisTemplate,
            boolean redisEnabled) {
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
    }

    public Decision acquire(Integer userId) {
        if (userId == null) {
            return new Decision(false, 60);
        }
        if (redisEnabled) {
            try {
                Decision decision = acquireDistributed(userId);
                redisAvailable.set(true);
                return decision;
            } catch (RuntimeException ex) {
                redisAvailable.set(false);
                // Avoid a log storm here: callers still receive safe local rate limiting.
                redisFailureCounter.incrementAndGet();
            }
        }
        return acquireLocal(userId);
    }

    public BackendStatus status() {
        if (!redisEnabled) {
            return new BackendStatus("local", true, redisFailureCounter.get());
        }
        return new BackendStatus(redisAvailable.get() ? "redis" : "local-fallback",
                redisAvailable.get(), redisFailureCounter.get());
    }

    private Decision acquireDistributed(Integer userId) {
        long now = clock.millis();
        long minuteBucket = now / WINDOW_MILLIS;
        String key = "cecsms:agent:rate:" + userId + ":" + minuteBucket;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(2));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter");
        }
        long remainingMillis = WINDOW_MILLIS - (now % WINDOW_MILLIS);
        return new Decision(count <= requestsPerMinute,
                Math.max(1, (remainingMillis + 999) / 1000));
    }

    private Decision acquireLocal(Integer userId) {
        long now = clock.millis();
        Window window = windows.compute(userId, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });

        if ((requestCounter.incrementAndGet() & 255) == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS * 2);
        }

        boolean allowed = window.count() <= requestsPerMinute;
        long remainingMillis = Math.max(0, WINDOW_MILLIS - (now - window.startedAt()));
        return new Decision(allowed, Math.max(1, (remainingMillis + 999) / 1000));
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }
    public record BackendStatus(String backend, boolean distributed, long fallbackCount) { }

    private record Window(long startedAt, int count) { }
}
