package com.cecsmsserve.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Admission and cost accounting for every paid model attempt, across accounts. */
@Component
public final class AiResourceGuard {
    private static final String PREFIX = "cecsms:{ai-resources}:";
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Shanghai");
    private final Limits limits;
    private final Clock clock;
    private final AiQuotaStore store;
    private final JsonMapper json;
    private final ThreadLocal<Lease> current = new ThreadLocal<>();
    private final AtomicBoolean accountingFailed = new AtomicBoolean();

    @Autowired
    public AiResourceGuard(Environment environment, ObjectProvider<StringRedisTemplate> redisProvider,
                           JsonMapper json) {
        this(Limits.from(environment), Clock.systemUTC(), configuredStore(environment, redisProvider), json);
    }

    AiResourceGuard(Limits limits, Clock clock, AiQuotaStore store, JsonMapper json) {
        this.limits = limits;
        this.clock = clock;
        this.store = store;
        this.json = json;
    }

    private static AiQuotaStore configuredStore(Environment environment, ObjectProvider<StringRedisTemplate> provider) {
        if (environment.getProperty("agent.redis.enabled", Boolean.class, false)) {
            StringRedisTemplate redis = provider.getIfAvailable();
            // A configured-but-missing Redis bean must never enable a local bypass.
            return redis == null ? null : new RedisAiQuotaStore(redis);
        }
        return new LocalAiQuotaStore(environment.getProperty("agent.ai.protection.local-max-entries", Integer.class, 4096));
    }

    public Lease acquireRequest(Integer userId, String remoteAddress) {
        if (userId == null || userId < 1) throw rejected(401, "请先登录");
        if (current.get() != null) throw rejected(503, "AI 请求上下文异常，请稍后重试");
        assertAvailable();
        long now = clock.millis();
        String user = "user:" + userId;
        String ip = "ip:" + addressKey(remoteAddress);
        String minute = ":minute:" + now / 60_000;
        String day = ":day:" + dayNumber(now);
        long minuteTtl = 120_000 - now % 60_000;
        long dayTtl = dayTtl(now);
        List<AiQuotaStore.Counter> counters = List.of(
                counter("requests:global" + minute, limits.globalPerMinute(), 1, minuteTtl),
                counter("requests:" + user + minute, limits.userPerMinute(), 1, minuteTtl),
                counter("requests:" + ip + minute, limits.ipPerMinute(), 1, minuteTtl),
                counter("requests:global" + day, limits.globalPerDay(), 1, dayTtl),
                counter("requests:" + user + day, limits.userPerDay(), 1, dayTtl),
                counter("requests:" + ip + day, limits.ipPerDay(), 1, dayTtl));
        List<AiQuotaStore.Slot> slots = List.of(
                new AiQuotaStore.Slot(PREFIX + "active:global", limits.globalConcurrency()),
                new AiQuotaStore.Slot(PREFIX + "active:" + user, limits.userConcurrency()),
                new AiQuotaStore.Slot(PREFIX + "active:" + ip, limits.ipConcurrency()));
        Lease lease = new Lease(UUID.randomUUID().toString(), slots, now + limits.leaseTtl().toMillis(),
                now + limits.runTimeout().toMillis());
        if (!admit(counters, lease, now, true)) throw rejected(429, "AI 体验额度或并发已满，请稍后重试");
        current.set(lease);
        return lease;
    }

    /** Called immediately before EACH HTTP attempt, including retries. No refund on failure. */
    public Reservation reserveModelCall(String provider, String model, Map<String, Object> body) {
        assertAvailable();
        Lease lease = current.get();
        long now = clock.millis();
        if (lease == null || lease.closed.get()) throw rejected(503, "AI 请求缺少资源授权");
        if (now >= lease.deadline || now >= lease.expiresAt) throw rejected(429, "本次 AI 请求已达到时间上限");
        byte[] encoded;
        try {
            encoded = json.writeValueAsBytes(body);
        } catch (RuntimeException ex) {
            throw rejected(400, "AI 请求格式无效");
        }
        if (encoded.length > limits.maxBodyBytes()) throw rejected(413, "AI 请求过大，请缩短会话或移除附件");
        Object maxValue = body.containsKey("max_output_tokens") ? body.get("max_output_tokens") : body.get("max_tokens");
        if (!(maxValue instanceof Number number) || number.longValue() < 1 || number.longValue() > 8000) {
            throw rejected(400, "AI 输出长度上限无效");
        }
        // UTF-8 bytes conservatively bound text tokenizer input. Include envelope
        // overhead and the full output allowance; never use a chars/4 estimate.
        long tokens = encoded.length + number.longValue() + 1024L;
        String day = ":day:" + dayNumber(now);
        String modelKey = digest((provider + ":" + model).getBytes(StandardCharsets.UTF_8));
        long ttl = dayTtl(now);
        List<AiQuotaStore.Counter> counters = List.of(
                counter("models:global:calls" + day, limits.modelCallsPerDay(), 1, ttl),
                counter("models:global:tokens" + day, limits.tokensPerDay(), tokens, ttl),
                counter("models:" + modelKey + ":calls" + day, limits.modelCallsPerDay(), 1, ttl),
                counter("models:" + modelKey + ":tokens" + day, limits.tokensPerDay(), tokens, ttl));
        if (!admit(counters, lease, now, false)) throw rejected(429, "今日 AI 体验预算已用完，请稍后再试");
        return new Reservation(tokens, List.of(counters.get(1), counters.get(3)));
    }

    private boolean admit(List<AiQuotaStore.Counter> counters, Lease lease, long now, boolean acquire) {
        try {
            return store.admit(counters, lease.slots, lease.token, now, lease.expiresAt, acquire);
        } catch (RuntimeException ex) {
            throw rejected(503, "AI 额度服务暂时不可用，请稍后重试");
        }
    }

    private void assertAvailable() {
        if (store == null || accountingFailed.get()) throw rejected(503, "AI 额度服务暂时不可用，请稍后重试");
    }

    private AiQuotaStore.Counter counter(String key, long limit, long amount, long ttl) {
        return new AiQuotaStore.Counter(PREFIX + key, limit, amount, ttl);
    }

    private long dayNumber(long now) {
        return Instant.ofEpochMilli(now).atZone(DAY_ZONE).toLocalDate().toEpochDay();
    }

    private long dayTtl(long now) {
        return Instant.ofEpochMilli(now).atZone(DAY_ZONE).toLocalDate().plusDays(1)
                .atStartOfDay(DAY_ZONE).toInstant().toEpochMilli() - now + limits.leaseTtl().toMillis();
    }

    private String addressKey(String address) {
        if (address == null || address.length() > 64 || !address.matches("[0-9a-fA-F:.]+")
                || (!address.contains(".") && !address.contains(":"))) throw rejected(400, "请求来源地址无效");
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            // Treat an IPv6 /64 as one source so rotating suffixes do not reset quotas.
            if (bytes.length == 16) Arrays.fill(bytes, 8, 16, (byte) 0);
            return digest(bytes);
        } catch (java.net.UnknownHostException ex) {
            throw rejected(400, "请求来源地址无效");
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static Rejected rejected(int status, String message) {
        return new Rejected(message, status, status == 429 ? 60 : 30);
    }

    public static final class Rejected extends AiProviderException {
        private final long retryAfterSeconds;

        private Rejected(String message, int status, long retryAfterSeconds) {
            super(message, status);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public final class Lease implements AutoCloseable {
        private final String token;
        private final List<AiQuotaStore.Slot> slots;
        private final long expiresAt;
        private final long deadline;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(String token, List<AiQuotaStore.Slot> slots, long expiresAt, long deadline) {
            this.token = token;
            this.slots = slots;
            this.expiresAt = expiresAt;
            this.deadline = deadline;
        }

        @Override
        public void close() {
            if (current.get() == this) current.remove();
            if (!closed.compareAndSet(false, true)) return;
            try {
                store.release(slots, token);
            } catch (RuntimeException ignored) {
                // No decrement fallback. Ownership expires in Redis; admission
                // remains Redis-only and denies requests while it is unavailable.
            }
        }
    }

    public final class Reservation {
        private final long reservedTokens;
        private final List<AiQuotaStore.Counter> tokenCounters;
        private final AtomicBoolean recorded = new AtomicBoolean();

        private Reservation(long reservedTokens, List<AiQuotaStore.Counter> tokenCounters) {
            this.reservedTokens = reservedTokens;
            this.tokenCounters = tokenCounters;
        }

        public void recordUsage(long totalTokens) {
            if (!recorded.compareAndSet(false, true) || totalTokens <= reservedTokens) return;
            try {
                store.charge(tokenCounters, Math.min(totalTokens, 1_000_000_000L) - reservedTokens, clock.millis());
            } catch (RuntimeException ex) {
                // If a response exceeded its reservation and accounting failed,
                // keep this process closed rather than silently losing the debt.
                accountingFailed.set(true);
                throw rejected(503, "AI 预算核对暂时失败，请稍后重试");
            }
        }

        public void recordUsage(Map<String, Object> response) {
            if (!(response.get("usage") instanceof Map<?, ?> usage)) return;
            Object total = usage.get("total_tokens");
            if (total instanceof Number number) recordUsage(Math.max(0, number.longValue()));
        }
    }

    record Limits(int globalConcurrency, int userConcurrency, int ipConcurrency,
                  int globalPerMinute, int userPerMinute, int ipPerMinute,
                  int globalPerDay, int userPerDay, int ipPerDay,
                  long modelCallsPerDay, long tokensPerDay, int maxBodyBytes,
                  Duration leaseTtl, Duration runTimeout) {
        Limits {
            if (globalConcurrency < 1 || userConcurrency < 1 || ipConcurrency < 1
                    || globalPerMinute < 1 || userPerMinute < 1 || ipPerMinute < 1
                    || globalPerDay < 1 || userPerDay < 1 || ipPerDay < 1
                    || modelCallsPerDay < 1 || tokensPerDay < 1 || maxBodyBytes < 1024 || maxBodyBytes > 65536
                    || runTimeout.isNegative() || runTimeout.isZero() || runTimeout.compareTo(Duration.ofMinutes(5)) > 0
                    || leaseTtl.compareTo(runTimeout.plusSeconds(120)) < 0 || leaseTtl.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("Invalid AI resource protection limits");
            }
        }

        static Limits defaults() {
            return new Limits(2, 1, 2, 20, 3, 6, 200, 20, 40, 200, 500_000,
                    65536, Duration.ofMinutes(5), Duration.ofSeconds(90));
        }

        static Limits from(Environment e) {
            Limits d = defaults();
            String p = "agent.ai.protection.";
            return new Limits(e.getProperty(p + "global-concurrency", Integer.class, d.globalConcurrency),
                    e.getProperty(p + "user-concurrency", Integer.class, d.userConcurrency),
                    e.getProperty(p + "ip-concurrency", Integer.class, d.ipConcurrency),
                    e.getProperty(p + "global-per-minute", Integer.class, d.globalPerMinute),
                    e.getProperty(p + "user-per-minute", Integer.class, d.userPerMinute),
                    e.getProperty(p + "ip-per-minute", Integer.class, d.ipPerMinute),
                    e.getProperty(p + "global-per-day", Integer.class, d.globalPerDay),
                    e.getProperty(p + "user-per-day", Integer.class, d.userPerDay),
                    e.getProperty(p + "ip-per-day", Integer.class, d.ipPerDay),
                    e.getProperty(p + "model-calls-per-day", Long.class, d.modelCallsPerDay),
                    e.getProperty(p + "tokens-per-day", Long.class, d.tokensPerDay),
                    e.getProperty(p + "max-body-bytes", Integer.class, d.maxBodyBytes),
                    DurationStyle.detectAndParse(e.getProperty(p + "lease-ttl", "5m")),
                    DurationStyle.detectAndParse(e.getProperty(p + "run-timeout", "90s")));
        }
    }
}
