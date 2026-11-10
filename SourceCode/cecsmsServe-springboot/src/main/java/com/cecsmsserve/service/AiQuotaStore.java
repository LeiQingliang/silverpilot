package com.cecsmsserve.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Storage operations must admit all counters and leases together, or none. */
interface AiQuotaStore {
    record Counter(String key, long limit, long increment, long ttlMillis) { }
    record Slot(String key, int limit) { }

    boolean admit(List<Counter> counters, List<Slot> slots, String token,
                  long now, long expiresAt, boolean acquire);
    void release(List<Slot> slots, String token);
    void charge(List<Counter> counters, long additionalTokens, long now);
}

/** Used ONLY when Redis is explicitly disabled, never after a Redis failure. */
final class LocalAiQuotaStore implements AiQuotaStore {
    private record Count(long value, long expiresAt) { }
    private final Map<String, Count> counters = new HashMap<>();
    private final Map<String, Map<String, Long>> leases = new HashMap<>();
    private final int maxEntries;

    LocalAiQuotaStore(int maxEntries) {
        this.maxEntries = Math.max(16, maxEntries);
    }

    @Override
    public synchronized boolean admit(List<Counter> requested, List<Slot> slots, String token,
                                      long now, long expiresAt, boolean acquire) {
        purge(now);
        for (Slot slot : slots) {
            Map<String, Long> active = leases.getOrDefault(slot.key(), Map.of());
            if (acquire ? active.size() >= slot.limit() : !active.containsKey(token)) return false;
        }
        int newEntries = 0;
        for (Counter counter : requested) {
            Count current = counters.get(counter.key());
            if (current == null) newEntries++;
            if ((current == null ? 0 : current.value()) > counter.limit() - counter.increment()) return false;
        }
        if (acquire) newEntries += (int) slots.stream().filter(slot -> !leases.containsKey(slot.key())).count();
        if (counters.size() + leases.size() + newEntries > maxEntries) return false;
        for (Counter counter : requested) {
            Count old = counters.get(counter.key());
            counters.put(counter.key(), new Count((old == null ? 0 : old.value()) + counter.increment(),
                    old == null ? now + counter.ttlMillis() : old.expiresAt()));
        }
        if (acquire) {
            for (Slot slot : slots) leases.computeIfAbsent(slot.key(), ignored -> new HashMap<>()).put(token, expiresAt);
        }
        return true;
    }

    @Override
    public synchronized void release(List<Slot> slots, String token) {
        for (Slot slot : slots) {
            Map<String, Long> active = leases.get(slot.key());
            if (active != null) {
                active.remove(token);
                if (active.isEmpty()) leases.remove(slot.key());
            }
        }
    }

    @Override
    public synchronized void charge(List<Counter> requested, long extra, long now) {
        purge(now);
        int newEntries = (int) requested.stream().filter(counter -> !counters.containsKey(counter.key())).count();
        if (counters.size() + leases.size() + newEntries > maxEntries) {
            throw new IllegalStateException("Local AI quota capacity reached");
        }
        for (Counter counter : requested) {
            Count old = counters.get(counter.key());
            counters.put(counter.key(), new Count(Math.addExact(old == null ? 0 : old.value(), extra),
                    old == null ? now + counter.ttlMillis() : old.expiresAt()));
        }
    }

    synchronized int size(long now) {
        purge(now);
        return counters.size() + leases.size();
    }

    private void purge(long now) {
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        leases.values().forEach(active -> active.entrySet().removeIf(entry -> entry.getValue() <= now));
        leases.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}

/** A common Redis hash tag keeps every operation compatible with Redis Cluster. */
final class RedisAiQuotaStore implements AiQuotaStore {
    static final DefaultRedisScript<Long> ADMIT = new DefaultRedisScript<>("""
            local acquire = ARGV[1] == 'acquire'
            local now = tonumber(ARGV[2])
            local token = ARGV[3]
            local expires = tonumber(ARGV[4])
            local n = tonumber(ARGV[5])
            local slots = tonumber(ARGV[6])
            for i = 1, slots do
              local key = KEYS[n + i]
              redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
              if acquire then
                if redis.call('ZCARD', key) >= tonumber(ARGV[6 + n * 3 + i]) then return 0 end
              elseif not redis.call('ZSCORE', key, token) then return 0 end
            end
            for i = 1, n do
              local offset = 6 + (i - 1) * 3
              local current = tonumber(redis.call('GET', KEYS[i]) or '0')
              if not current or current < 0 then return -1 end
              if current > tonumber(ARGV[offset + 1]) - tonumber(ARGV[offset + 2]) then return 0 end
            end
            for i = 1, n do
              local offset = 6 + (i - 1) * 3
              redis.call('INCRBY', KEYS[i], ARGV[offset + 2])
              if redis.call('PTTL', KEYS[i]) < 0 then redis.call('PEXPIRE', KEYS[i], ARGV[offset + 3]) end
            end
            if acquire then
              for i = 1, slots do
                local key = KEYS[n + i]
                redis.call('ZADD', key, expires, token)
                redis.call('PEXPIRE', key, expires - now)
              end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            for _, key in ipairs(KEYS) do
              redis.call('ZREM', key, ARGV[1])
              if redis.call('ZCARD', key) == 0 then redis.call('DEL', key) end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CHARGE = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
              redis.call('INCRBY', key, ARGV[1])
              if redis.call('PTTL', key) < 0 then redis.call('PEXPIRE', key, ARGV[i + 1]) end
            end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    RedisAiQuotaStore(StringRedisTemplate redis) {
        this.redis = java.util.Objects.requireNonNull(redis);
    }

    @Override
    public boolean admit(List<Counter> counters, List<Slot> slots, String token,
                         long now, long expiresAt, boolean acquire) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>(List.of(acquire ? "acquire" : "reserve",
                Long.toString(now), token, Long.toString(expiresAt), Integer.toString(counters.size()),
                Integer.toString(slots.size())));
        for (Counter counter : counters) {
            keys.add(counter.key());
            args.add(Long.toString(counter.limit()));
            args.add(Long.toString(counter.increment()));
            args.add(Long.toString(counter.ttlMillis()));
        }
        for (Slot slot : slots) {
            keys.add(slot.key());
            args.add(Integer.toString(slot.limit()));
        }
        Long result = redis.execute(ADMIT, keys, args.toArray());
        if (result == null || result < 0) throw new IllegalStateException("Redis AI quota unavailable");
        return result == 1;
    }

    @Override
    public void release(List<Slot> slots, String token) {
        Long result = redis.execute(RELEASE, slots.stream().map(Slot::key).toList(), token);
        if (result == null) throw new IllegalStateException("Redis AI lease release unavailable");
    }

    @Override
    public void charge(List<Counter> counters, long extra, long now) {
        List<String> args = new ArrayList<>(List.of(Long.toString(extra)));
        counters.forEach(counter -> args.add(Long.toString(counter.ttlMillis())));
        Long result = redis.execute(CHARGE, counters.stream().map(Counter::key).toList(), args.toArray());
        if (result == null) throw new IllegalStateException("Redis AI quota accounting unavailable");
    }
}
