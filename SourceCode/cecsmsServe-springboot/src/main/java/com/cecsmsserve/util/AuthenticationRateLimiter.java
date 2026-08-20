package com.cecsmsserve.util;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthenticationRateLimiter {

    private static final Duration CAPTCHA_WINDOW = Duration.ofMinutes(5);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(10);

    private final Map<String, Window> localWindows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final int captchaLimit;
    private final int loginLimit;
    private final Clock clock;
    private final AtomicLong cleanupCounter = new AtomicLong();

    @Autowired
    public AuthenticationRateLimiter(
            @Value("${app.security.auth-rate-limit.captcha-per-five-minutes:30}") int captchaLimit,
            @Value("${app.security.auth-rate-limit.login-per-ten-minutes:8}") int loginLimit,
            @Value("${agent.redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this(captchaLimit, loginLimit, Clock.systemUTC(),
                redisEnabled ? redisProvider.getIfAvailable() : null, redisEnabled);
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
        this.captchaLimit = Math.max(1, captchaLimit);
        this.loginLimit = Math.max(1, loginLimit);
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
    }

    public Decision acquireCaptcha(String remoteAddress) {
        return acquire("captcha", safeSubject(remoteAddress), captchaLimit, CAPTCHA_WINDOW);
    }

    public Decision acquireLogin(String remoteAddress, String loginName) {
        return acquire("login", safeSubject(remoteAddress) + ":" + safeSubject(loginName), loginLimit, LOGIN_WINDOW);
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
            localWindows.entrySet().removeIf(entry -> entry.getValue().bucket() < bucket - 1);
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
    private record Window(long bucket, int count) { }
}
