package com.cecsmsserve.util;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthenticationRateLimiter {

    private static final Duration CAPTCHA_WINDOW = Duration.ofMinutes(5);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final DefaultRedisScript<Long> REGISTRATION_SCRIPT = new DefaultRedisScript<>("""
            local ip = tonumber(redis.call('GET', KEYS[1]) or '0')
            local total = tonumber(redis.call('GET', KEYS[2]) or '0')
            if ip >= tonumber(ARGV[1]) or total >= tonumber(ARGV[2]) then return 0 end
            for _, key in ipairs(KEYS) do
                local count = redis.call('INCR', key)
                if count == 1 then redis.call('PEXPIRE', key, ARGV[3]) end
            end
            return 1
            """, Long.class);

    private final Map<String, Window> localWindows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final int captchaLimit;
    private final int loginLimit;
    private final int registrationLimit;
    private final int registrationGlobalLimit;
    private final boolean registrationRedisFailClosed;
    private final Clock clock;
    private final AtomicLong cleanupCounter = new AtomicLong();

    @Autowired
    public AuthenticationRateLimiter(
            @Value("${app.security.auth-rate-limit.captcha-per-five-minutes:30}") int captchaLimit,
            @Value("${app.security.auth-rate-limit.login-per-ten-minutes:8}") int loginLimit,
            @Value("${app.security.registration.per-hour:3}") int registrationLimit,
            @Value("${app.security.registration.global-per-hour:30}") int registrationGlobalLimit,
            @Value("${app.security.registration.redis-fail-closed:false}") boolean registrationRedisFailClosed,
            @Value("${agent.redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this(captchaLimit, loginLimit, Clock.systemUTC(),
                redisEnabled ? redisProvider.getIfAvailable() : null, redisEnabled,
                registrationLimit, registrationGlobalLimit, registrationRedisFailClosed);
    }

    AuthenticationRateLimiter(int captchaLimit, int loginLimit, Clock clock) {
        this(captchaLimit, loginLimit, clock, null, false);
    }

    AuthenticationRateLimiter(
            int captchaLimit,
            int loginLimit,
            Clock clock,
            StringRedisTemplate redisTemplate,
            boolean redisEnabled) {
        this(captchaLimit, loginLimit, clock, redisTemplate, redisEnabled, 3, 30, false);
    }

    AuthenticationRateLimiter(
            int captchaLimit, int loginLimit, Clock clock,
            StringRedisTemplate redisTemplate, boolean redisEnabled,
            int registrationLimit, int registrationGlobalLimit, boolean registrationRedisFailClosed) {
        this.captchaLimit = Math.max(1, captchaLimit);
        this.loginLimit = Math.max(1, loginLimit);
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
        this.registrationLimit = Math.max(1, registrationLimit);
        this.registrationGlobalLimit = Math.max(1, registrationGlobalLimit);
        this.registrationRedisFailClosed = registrationRedisFailClosed;
    }

    public Decision acquireCaptcha(String remoteAddress) {
        return acquire("captcha", safeSubject(remoteAddress), captchaLimit, CAPTCHA_WINDOW);
    }

    public Decision acquireLogin(String remoteAddress, String loginName) {
        return acquire("login", safeSubject(remoteAddress) + ":" + safeSubject(loginName), loginLimit, LOGIN_WINDOW);
    }

    /** One atomic reservation across IP and site budgets after a valid one-time CAPTCHA. */
    public RegistrationDecision acquireRegistration(String remoteAddress) {
        long now = clock.millis();
        long duration = REGISTRATION_WINDOW.toMillis();
        long bucket = now / duration;
        long remaining = duration - now % duration;
        long retry = Math.max(1, (remaining + 999) / 1000);
        // The shared hash tag also keeps the multi-key script compatible with Redis Cluster.
        String prefix = "cecsms:security:{registration}:" + bucket + ":";
        String ipKey = prefix + "ip:" + digest(safeSubject(remoteAddress));
        String globalKey = prefix + "global";
        if (redisEnabled) {
            try {
                Long allowed = redisTemplate.execute(REGISTRATION_SCRIPT, List.of(ipKey, globalKey),
                        String.valueOf(registrationLimit), String.valueOf(registrationGlobalLimit),
                        String.valueOf(remaining + 60_000));
                if (allowed != null) return new RegistrationDecision(allowed == 1, true, retry);
            } catch (RuntimeException ignored) { /* production registration must not bypass its shared budget */ }
        }
        if (registrationRedisFailClosed) return new RegistrationDecision(false, false, retry);
        synchronized (localWindows) {
            // Local mode is for development; lock both budgets together to prevent concurrent oversubscription.
            localWindows.entrySet().removeIf(entry -> entry.getKey().startsWith("cecsms:security:{registration}:")
                    && entry.getValue().bucket() < bucket);
            Window ip = localWindows.get(ipKey);
            Window global = localWindows.get(globalKey);
            int ipCount = ip == null ? 0 : ip.count();
            int globalCount = global == null ? 0 : global.count();
            if (ipCount >= registrationLimit || globalCount >= registrationGlobalLimit) {
                return new RegistrationDecision(false, true, retry);
            }
            localWindows.put(ipKey, new Window(bucket, ipCount + 1));
            localWindows.put(globalKey, new Window(bucket, globalCount + 1));
            return new RegistrationDecision(true, true, retry);
        }
    }

    public void resetLogin(String remoteAddress, String loginName) {
        String subject = safeSubject(remoteAddress) + ":" + safeSubject(loginName);
        long bucket = clock.millis() / LOGIN_WINDOW.toMillis();
        String key = key("login", subject, bucket);
        localWindows.remove(key);
        if (redisEnabled) {
            try { redisTemplate.delete(key); }
            catch (RuntimeException ignored) { /* local limiter remains the safe fallback */ }
        }
    }

    private Decision acquire(String namespace, String subject, int limit, Duration duration) {
        long now = clock.millis();
        long windowMillis = duration.toMillis();
        long bucket = now / windowMillis;
        String key = key(namespace, subject, bucket);
        if (redisEnabled) {
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                redisTemplate.expire(key, duration.plusMinutes(1));
                if (count != null) return decision(count, limit, now, windowMillis);
            } catch (RuntimeException ignored) { /* fall through to bounded in-process protection */ }
        }
        Window window = localWindows.compute(key, (ignored, current) -> new Window(
                bucket,
                current == null || current.bucket() != bucket ? 1 : current.count() + 1));
        if ((cleanupCounter.incrementAndGet() & 255) == 0) {
            localWindows.entrySet().removeIf(entry -> entry.getKey().startsWith("cecsms:security:" + namespace + ":")
                    && entry.getValue().bucket() < bucket - 1);
        }
        return decision(window.count(), limit, now, windowMillis);
    }

    private Decision decision(long count, int limit, long now, long windowMillis) {
        long retry = Math.max(1, (windowMillis - (now % windowMillis) + 999) / 1000);
        return new Decision(count <= limit, retry);
    }

    private String key(String namespace, String subject, long bucket) {
        return "cecsms:security:" + namespace + ":" + digest(subject) + ":" + bucket;
    }

    private String safeSubject(String value) {
        String normalized = value == null ? "unknown" : value.trim().toLowerCase();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }
    public record RegistrationDecision(boolean allowed, boolean available, long retryAfterSeconds) { }
    private record Window(long bucket, int count) { }
}
