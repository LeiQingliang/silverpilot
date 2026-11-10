package com.cecsmsserve.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only: point this at an isolated test Redis, never production. */
@EnabledIfEnvironmentVariable(named = "CECSMS_TEST_REDIS_HOST", matches = ".+")
class AiQuotaRedisIntegrationTests {
    private LettuceConnectionFactory connections;
    private StringRedisTemplate redis;
    private RedisAiQuotaStore store;
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
        store = new RedisAiQuotaStore(redis);
        prefix = "silverpilot:quota-test:{" + UUID.randomUUID() + "}:";
    }

    @AfterEach
    void disconnect() {
        if (redis != null && !createdKeys.isEmpty()) redis.delete(createdKeys);
        if (connections != null) connections.destroy();
    }

    @Test
    void luaRejectsWholeAdmissionWithoutConsumingEarlierCountersAndSetsExpiryAtomically() {
        String first = key("first"), second = key("second"), active = key("active");
        long now = System.currentTimeMillis();
        var slots = List.of(new AiQuotaStore.Slot(active, 1));
        assertFalse(store.admit(List.of(new AiQuotaStore.Counter(first, 10, 1, 5000),
                new AiQuotaStore.Counter(second, 0, 1, 5000)), slots, "one", now, now + 5000, true));
        assertNull(redis.opsForValue().get(first));
        assertNull(redis.opsForValue().get(second));
        assertFalse(redis.hasKey(active));
        assertTrue(store.admit(List.of(new AiQuotaStore.Counter(first, 1, 1, 5000)), slots,
                "one", now, now + 5000, true));
        assertTrue(redis.getExpire(first, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(active, TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void luaRecoversExpiredLeaseAndLateReleasePreservesNewOwner() {
        long now = System.currentTimeMillis();
        var slots = List.of(new AiQuotaStore.Slot(key("active"), 1));
        assertTrue(store.admit(List.of(), slots, "old", now, now + 5000, true));
        assertFalse(store.admit(List.of(), slots, "new", now, now + 5000, true));
        assertTrue(store.admit(List.of(), slots, "new", now + 5001, now + 10000, true));
        store.release(slots, "old");
        assertFalse(store.admit(List.of(), slots, "third", now + 5002, now + 10000, true));
        assertTrue(store.admit(List.of(), slots, "new", now + 5002, now + 10000, false));
        store.release(slots, "new");
        assertFalse(store.admit(List.of(), slots, "new", now + 5002, now + 10000, false));
    }

    @Test
    void concurrentRedisScriptsAdmitOnlyGlobalCapacity() throws Exception {
        long now = System.currentTimeMillis();
        var slots = List.of(new AiQuotaStore.Slot(key("active"), 2));
        String counter = key("requests");
        try (var executor = Executors.newFixedThreadPool(16)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 16; i++) {
                final String token = "request-" + i;
                tasks.add(() -> store.admit(List.of(new AiQuotaStore.Counter(counter, 100, 1, 30000)),
                        slots, token, now, now + 30000, true));
            }
            int admitted = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) admitted++;
            assertEquals(2, admitted);
            assertEquals("2", redis.opsForValue().get(counter));
        }
    }

    private String key(String suffix) {
        String value = prefix + suffix;
        createdKeys.add(value);
        return value;
    }
}
