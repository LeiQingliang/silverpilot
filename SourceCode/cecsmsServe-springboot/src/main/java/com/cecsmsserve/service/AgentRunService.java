package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.AgentRun;
import com.cecsmsserve.mapper.AgentRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRunService {

    public static final String COMPLETED = "COMPLETED";
    public static final String WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    public static final String DEGRADED = "DEGRADED";
    public static final String FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);
    private final AgentRunMapper mapper;
    private final MeterRegistry meterRegistry;

    public AgentRunService(AgentRunMapper mapper) {
        this(mapper, (MeterRegistry) null);
    }

    @Autowired
    public AgentRunService(AgentRunMapper mapper, ObjectProvider<MeterRegistry> meterRegistry) {
        this(mapper, meterRegistry.getIfAvailable());
    }

    private AgentRunService(AgentRunMapper mapper, MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    /** Observability must never turn a successful business action into a failure. */
    public void record(RunRecord record) {
        try {
            AgentRun run = new AgentRun();
            run.setRunId(record.runId());
            run.setUserId(record.userId());
            run.setProvider(value(record.provider(), "unresolved"));
            run.setModel(value(record.model(), "unresolved"));
            run.setInputModality(value(record.inputModality(), "TEXT"));
            run.setStatus(value(record.status(), FAILED));
            run.setLatencyMs(Math.max(0, record.latencyMs()));
            run.setLlmCalls(Math.max(0, record.llmCalls()));
            run.setToolCalls(Math.max(0, record.toolCalls()));
            run.setSuccessfulTools(Math.max(0, record.successfulTools()));
            run.setFailedTools(Math.max(0, record.failedTools()));
            run.setConfirmationRequired(record.confirmationRequired());
            run.setPromptVersion(truncate(record.promptVersion(), 64));
            run.setPromptTokens(Math.max(0, record.promptTokens()));
            run.setCompletionTokens(Math.max(0, record.completionTokens()));
            run.setTotalTokens(Math.max(0, record.totalTokens()));
            run.setErrorType(truncate(record.errorType(), 64));
            run.setCreatedAt(record.startedAt());
            run.setCompletedAt(LocalDateTime.now());
            mapper.insert(run);
            recordMetrics(run);
        } catch (Exception ex) {
            log.warn("Unable to persist Agent run {}: {}", record.runId(), ex.getMessage());
        }
    }

    public AnalyticsView analytics(Integer userId, int requestedDays) {
        return calculate(load(userId, requestedDays), requestedDays);
    }

    public AnalyticsView systemAnalytics(int requestedDays) {
        return calculate(load(null, requestedDays), requestedDays);
    }

    private List<AgentRun> load(Integer userId, int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 30));
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .ge(AgentRun::getCreatedAt, LocalDateTime.now().minusDays(days))
                .orderByDesc(AgentRun::getId)
                .last("LIMIT 5000");
        if (userId != null) query.eq(AgentRun::getUserId, userId);
        return mapper.selectList(query);
    }

    private AnalyticsView calculate(List<AgentRun> runs, int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 30));
        long successfulRuns = runs.stream().filter(run -> !FAILED.equals(run.getStatus())).count();
        long waiting = runs.stream().filter(run -> WAITING_CONFIRMATION.equals(run.getStatus())).count();
        long degraded = runs.stream().filter(run -> DEGRADED.equals(run.getStatus())).count();
        long totalToolCalls = runs.stream().mapToLong(run -> number(run.getToolCalls())).sum();
        long totalTokens = runs.stream().mapToLong(run -> number(run.getTotalTokens())).sum();
        long successfulTools = runs.stream().mapToLong(run -> number(run.getSuccessfulTools())).sum();
        long averageLatency = Math.round(runs.stream().mapToLong(run -> number(run.getLatencyMs()))
                .average().orElse(0));
        double successRate = ratio(successfulRuns, runs.size());
        double toolSuccessRate = ratio(successfulTools, totalToolCalls);

        Map<String, Long> providers = new LinkedHashMap<>();
        Map<String, Long> modalities = new LinkedHashMap<>();
        for (AgentRun run : runs) {
            providers.merge(value(run.getProvider(), "unknown"), 1L, Long::sum);
            modalities.merge(value(run.getInputModality(), "UNKNOWN"), 1L, Long::sum);
        }
        List<RunView> recent = runs.stream().limit(8).map(this::toView).toList();
        return new AnalyticsView(days, runs.size(), successRate, averageLatency, totalToolCalls,
                totalTokens, toolSuccessRate, waiting, degraded, providers, modalities, recent);
    }

    private RunView toView(AgentRun run) {
        return new RunView(run.getRunId(), run.getUserId(), run.getProvider(), run.getModel(), run.getInputModality(),
                run.getStatus(), number(run.getLatencyMs()), number(run.getLlmCalls()),
                number(run.getToolCalls()), Boolean.TRUE.equals(run.getConfirmationRequired()),
                number(run.getTotalTokens()), run.getPromptVersion(), run.getCreatedAt());
    }

    private void recordMetrics(AgentRun run) {
        if (meterRegistry == null) return;
        String provider = safeTag(run.getProvider(), "unknown");
        String status = safeTag(run.getStatus(), "unknown");
        meterRegistry.counter("cecsms.agent.runs", "provider", provider, "status", status).increment();
        meterRegistry.counter("cecsms.agent.tokens", "provider", provider)
                .increment(number(run.getTotalTokens()));
        Timer.builder("cecsms.agent.latency")
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis(number(run.getLatencyMs())));
    }

    private String safeTag(String value, String fallback) {
        String normalized = value(value, fallback).toLowerCase();
        return normalized.matches("[a-z0-9_-]{1,32}") ? normalized : fallback;
    }

    private double ratio(long numerator, long denominator) {
        if (denominator == 0) return 0;
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private long number(Number value) { return value == null ? 0 : value.longValue(); }
    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    public record RunRecord(
            String runId, Integer userId, String provider, String model, String inputModality,
            String status, long latencyMs, int llmCalls, int toolCalls, int successfulTools,
            int failedTools, boolean confirmationRequired, String errorType, String promptVersion,
            int promptTokens, int completionTokens, int totalTokens, LocalDateTime startedAt) {
        public RunRecord(
                String runId, Integer userId, String provider, String model, String inputModality,
                String status, long latencyMs, int llmCalls, int toolCalls, int successfulTools,
                int failedTools, boolean confirmationRequired, String errorType, LocalDateTime startedAt) {
            this(runId, userId, provider, model, inputModality, status, latencyMs, llmCalls,
                    toolCalls, successfulTools, failedTools, confirmationRequired, errorType,
                    null, 0, 0, 0, startedAt);
        }
    }

    public record RunView(
            String runId, Integer userId, String provider, String model, String inputModality, String status,
            long latencyMs, long llmCalls, long toolCalls, boolean confirmationRequired,
            long totalTokens, String promptVersion, LocalDateTime createdAt) { }

    public record AnalyticsView(
            int days, long totalRuns, double successRate, long averageLatencyMs, long toolCalls,
            long totalTokens, double toolSuccessRate, long waitingConfirmation, long degradedRuns,
            Map<String, Long> providers,
            Map<String, Long> modalities, List<RunView> recentRuns) { }
}
