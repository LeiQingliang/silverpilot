package com.cecsmsserve.service;

import com.cecsmsserve.entity.AgentRun;
import com.cecsmsserve.mapper.AgentRunMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunServiceTests {

    @Test
    void recordsPrivacySafeMetricsAndCalculatesAnalytics() {
        AgentRunMapper mapper = mock(AgentRunMapper.class);
        AgentRunService service = new AgentRunService(mapper);
        service.record(new AgentRunService.RunRecord(
                "run-1", 7, "deepseek", "model", "TEXT", AgentRunService.COMPLETED,
                1200, 2, 2, 2, 0, false, null, LocalDateTime.now()));
        verify(mapper).insert(any(AgentRun.class));

        AgentRun ok = run("deepseek", AgentRunService.COMPLETED, 1000, 2, 2);
        AgentRun failed = run("doubao", AgentRunService.FAILED, 3000, 1, 0);
        when(mapper.selectList(any())).thenReturn(List.of(ok, failed));

        AgentRunService.AnalyticsView analytics = service.analytics(7, 7);
        assertEquals(2, analytics.totalRuns());
        assertEquals(50.0, analytics.successRate());
        assertEquals(2000, analytics.averageLatencyMs());
        assertEquals(3, analytics.toolCalls());
        assertEquals(66.7, analytics.toolSuccessRate());
    }

    private AgentRun run(String provider, String status, long latency, int calls, int successes) {
        AgentRun run = new AgentRun();
        run.setRunId(provider + status);
        run.setProvider(provider);
        run.setModel("model");
        run.setInputModality("TEXT");
        run.setStatus(status);
        run.setLatencyMs(latency);
        run.setLlmCalls(1);
        run.setToolCalls(calls);
        run.setSuccessfulTools(successes);
        run.setFailedTools(calls - successes);
        run.setTotalTokens(50);
        run.setCreatedAt(LocalDateTime.now());
        return run;
    }
}
