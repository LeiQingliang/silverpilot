package com.cecsmsserve.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores short-lived, one-time CAPTCHA challenges.
 *
 * <p>Redis is used when configured so a challenge can be verified by any
 * application instance. If Redis cannot be reached while issuing a challenge,
 * the service degrades to a bounded in-process store instead of accepting an
 * unverifiable challenge or growing memory without limit.</p>
 */
@Component
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final String REDIS_KEY_PREFIX = "cecsms:security:captcha:";

    private final Map<String, CaptchaEntry> localEntries = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final Duration ttl;
    private final int maxLocalEntries;
    private final Clock clock;
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean();

    @Autowired
    public CaptchaService(
            @Value("${app.security.captcha.ttl:5m}") Duration ttl,
            @Value("${app.security.captcha.max-local-entries:10000}") int maxLocalEntries,
            @Value("${agent.redis.enabled:false}") boolean redisEnabled,
            ObjectProvider<StringRedisTemplate> redisProvider) {
        this(ttl, maxLocalEntries, Clock.systemUTC(),
                redisEnabled ? redisProvider.getIfAvailable() : null, redisEnabled);
    }

    CaptchaService(
            Duration ttl,
            int maxLocalEntries,
            Clock clock,
            StringRedisTemplate redisTemplate,
            boolean redisEnabled) {
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        this.maxLocalEntries = Math.max(1, maxLocalEntries);
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled && redisTemplate != null;
    }

    public boolean isValidKey(String key) {
        return key != null && key.matches("[A-Za-z0-9_-]{1,64}");
    }

    public boolean store(String key, String code) {
        if (!isValidKey(key) || code == null || code.isBlank()) {
            return false;
        }
        String normalizedCode = normalizeCode(code);
        if (redisEnabled) {
            try {
                redisTemplate.opsForValue().set(redisKey(key), normalizedCode, ttl);
                localEntries.remove(key);
                redisFailureLogged.set(false);
                return true;
            } catch (RuntimeException ex) {
                logRedisFallbackOnce(ex);
            }
        }
        return storeLocally(key, normalizedCode);
    }

    public boolean consumeMatches(String key, String submittedCode) {
        if (!isValidKey(key)) {
            return false;
        }

        CaptchaEntry localEntry = localEntries.remove(key);
        String expected = null;
        if (redisEnabled) {
            try {
                expected = redisTemplate.opsForValue().getAndDelete(redisKey(key));
                redisFailureLogged.set(false);
            } catch (RuntimeException ex) {
                logRedisFallbackOnce(ex);
            }
        }
        if (expected == null && localEntry != null && localEntry.expiresAt().isAfter(clock.instant())) {
            expected = localEntry.value();
        }
        if (expected == null) {
            return false;
        }
        if (submittedCode == null || submittedCode.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizeCode(submittedCode).getBytes(StandardCharsets.UTF_8));
    }

    private boolean storeLocally(String key, String code) {
        synchronized (localEntries) {
            Instant now = clock.instant();
            localEntries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            if (!localEntries.containsKey(key) && localEntries.size() >= maxLocalEntries) {
                return false;
            }
            localEntries.put(key, new CaptchaEntry(code, now.plus(ttl)));
            return true;
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String redisKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return REDIS_KEY_PREFIX + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void logRedisFallbackOnce(RuntimeException ex) {
        if (redisFailureLogged.compareAndSet(false, true)) {
            log.warn("Redis CAPTCHA storage is unavailable; using bounded local fallback: {}",
                    ex.getClass().getSimpleName());
        }
    }

    private record CaptchaEntry(String value, Instant expiresAt) { }
}
