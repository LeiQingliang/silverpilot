package com.cecsmsserve.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only: use an isolated test Redis, never production. */
@EnabledIfEnvironmentVariable(named = "CECSMS_TEST_REDIS_HOST", matches = ".+")
class RegistrationRedisIntegrationTests {
    private LettuceConnectionFactory connections;
    private StringRedisTemplate redis;
    private Clock clock;
    private String prefix;
    private final List<String> createdKeys = new ArrayList<>();

    @BeforeEach
    void connect() {
        var configuration = new RedisStandaloneConfiguration(System.getenv("CECSMS_TEST_REDIS_HOST"),
                Integer.parseInt(System.getenv().getOrDefault("CECSMS_TEST_REDIS_PORT", "6379")));
        String password = System.getenv("CECSMS_TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) configuration.setPassword(password);
        connections = new LettuceConnectionFactory(configuration);
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        // A randomized, far-future hour isolates the global registration key as well as IP keys.
        long bucket = 10_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000L);
        clock = Clock.fixed(Instant.ofEpochMilli(bucket * 3_600_000L + 1_800_000L), ZoneOffset.UTC);
        prefix = "cecsms:security:{registration}:" + bucket + ":";
        createdKeys.add(prefix + "global");
    }

    @AfterEach
    void disconnect() {
        if (redis != null && !createdKeys.isEmpty()) redis.delete(createdKeys);
        if (connections != null) connections.destroy();
    }

    @Test
    void luaSetsExpiryAndDoesNotConsumeGlobalCapacityForAnAlreadyLimitedIp() throws Exception {
        AuthenticationRateLimiter limiter = limiter(1, 2);
        rememberIp("first");
        rememberIp("second");
        rememberIp("third");
        assertTrue(limiter.acquireRegistration("first").allowed());
        assertFalse(limiter.acquireRegistration("first").allowed());
        assertTrue(limiter.acquireRegistration("second").allowed());
        assertFalse(limiter.acquireRegistration("third").allowed());
        assertEquals("2", redis.opsForValue().get(prefix + "global"));
        assertTrue(redis.getExpire(prefix + "global", TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(ipKey("first"), TimeUnit.MILLISECONDS) > 0);
        assertFalse(redis.hasKey(ipKey("third")));
    }

    @Test
    void multipleApplicationInstancesCannotOversubscribeTheGlobalRegistrationBudget() throws Exception {
        AuthenticationRateLimiter first = limiter(3, 5);
        AuthenticationRateLimiter second = limiter(3, 5);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String source = "concurrent-test-" + i;
            rememberIp(source);
            AuthenticationRateLimiter instance = i % 2 == 0 ? first : second;
            tasks.add(() -> instance.acquireRegistration(source).allowed());
        }
        try (var executor = Executors.newFixedThreadPool(16)) {
            int accepted = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) accepted++;
            assertEquals(5, accepted);
            assertEquals("5", redis.opsForValue().get(prefix + "global"));
        }
    }

    @Test
    void oneCaptchaCannotAuthorizeTwoConcurrentRegistrations() throws Exception {
        String challenge = "registration-test-" + UUID.randomUUID();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(challenge.getBytes(StandardCharsets.UTF_8));
        createdKeys.add("cecsms:security:captcha:" + HexFormat.of().formatHex(digest, 0, 16));
        CaptchaService first = new CaptchaService(Duration.ofMinutes(5), 10, Clock.systemUTC(), redis, true, true);
        CaptchaService second = new CaptchaService(Duration.ofMinutes(5), 10, Clock.systemUTC(), redis, true, true);
        assertTrue(first.store(challenge, "AB7Z"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<CaptchaService.Verification>>of(
                    () -> first.consumeForRegistration(challenge, "AB7Z"),
                    () -> second.consumeForRegistration(challenge, "AB7Z")));
            int matched = 0;
            for (var result : results) if (result.get() == CaptchaService.Verification.MATCH) matched++;
            assertEquals(1, matched);
        }
    }

    private AuthenticationRateLimiter limiter(int perIp, int global) {
        return new AuthenticationRateLimiter(30, 8, clock, redis, true, perIp, global, true);
    }

    private void rememberIp(String source) throws Exception {
        createdKeys.add(ipKey(source));
    }

    private String ipKey(String source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        return prefix + "ip:" + HexFormat.of().formatHex(digest, 0, 12);
    }
}
