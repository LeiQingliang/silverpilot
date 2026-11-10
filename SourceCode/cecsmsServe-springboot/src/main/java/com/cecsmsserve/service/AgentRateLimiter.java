package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AgentRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_LOCAL_USERS = 4096;
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if not count or count < 0 then return -1 end
            if count >= tonumber(ARGV[1]) then return 0 end
            redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return 1
            """, Long.class);

    private final Map<Integer, Window> windows = new HashMap<>();
    private final int requestsPerMinute;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
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
        this.redisEnabled = redisEnabled;
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
                redisFailureCounter.incrementAndGet();
                return new Decision(false, 30);
            }
        }
        return acquireLocal(userId);
    }

    public BackendStatus status() {
        if (!redisEnabled) {
            return new BackendStatus("local", true, redisFailureCounter.get());
        }
        return new BackendStatus(redisAvailable.get() ? "redis" : "redis-unavailable",
                redisAvailable.get(), redisFailureCounter.get());
    }

    private Decision acquireDistributed(Integer userId) {
        long now = clock.millis();
        long minuteBucket = now / WINDOW_MILLIS;
        String key = "cecsms:agent:rate:" + userId + ":" + minuteBucket;
        if (redisTemplate == null) throw new IllegalStateException("Redis is required but unavailable");
        Long result = redisTemplate.execute(ACQUIRE, List.of(key), Integer.toString(requestsPerMinute), "120000");
        if (result == null || result < 0) {
            throw new IllegalStateException("Redis did not return a rate-limit counter");
        }
        long remainingMillis = WINDOW_MILLIS - (now % WINDOW_MILLIS);
        return new Decision(result == 1,
                Math.max(1, (remainingMillis + 999) / 1000));
    }

    private synchronized Decision acquireLocal(Integer userId) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        if (!windows.containsKey(userId) && windows.size() >= MAX_LOCAL_USERS) return new Decision(false, 60);
        Window window = windows.compute(userId, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), Math.min(requestsPerMinute + 1, current.count() + 1));
        });

        boolean allowed = window.count() <= requestsPerMinute;
        long remainingMillis = Math.max(0, WINDOW_MILLIS - (now - window.startedAt()));
        return new Decision(allowed, Math.max(1, (remainingMillis + 999) / 1000));
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }
    public record BackendStatus(String backend, boolean distributed, long fallbackCount) { }

    private record Window(long startedAt, int count) { }
}
