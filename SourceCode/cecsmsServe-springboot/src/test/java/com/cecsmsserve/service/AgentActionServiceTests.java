package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.AgentAction;
import com.cecsmsserve.mapper.AgentActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AgentActionServiceTests {

    @Autowired
    private AgentActionMapper mapper;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void clearActions() {
        mapper.delete(new LambdaQueryWrapper<AgentAction>().gt(AgentAction::getId, 0));
    }

    @Test
    void confirmedActionExecutesOnlyOnceAndClearsArguments() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentActionService service = new AgentActionService(
                mapper, executor, jsonMapper, Duration.ofMinutes(10), 20);
        ToolExecutor.ToolPlan plan = new ToolExecutor.ToolPlan(
                "cancel_service_order", Map.of("orderId", 7), "取消服务订单 #7", true);
        when(executor.prepare(eq("cancel_service_order"), anyMap(), eq(17))).thenReturn(plan);
        when(executor.execute(plan, 17)).thenReturn(new ToolExecutor.ToolResult(true, "订单已取消"));

        AgentActionService.ActionView pending = service.createPending(17, plan);
        AgentActionService.ActionExecution first = service.confirm(17, pending.confirmationToken());
        AgentActionService.ActionExecution second = service.confirm(17, pending.confirmationToken());

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(AgentActionService.SUCCEEDED, second.action().status());
        verify(executor, times(1)).execute(plan, 17);
        AgentAction stored = mapper.selectById(pending.id());
        assertNull(stored.getArgumentsJson());
        assertNull(second.action().confirmationToken());
    }

    @Test
    void stateChangeDuringConfirmationBecomesTerminalFailure() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentActionService service = new AgentActionService(
                mapper, executor, jsonMapper, Duration.ofMinutes(10), 20);
        ToolExecutor.ToolPlan plan = new ToolExecutor.ToolPlan(
                "cancel_service_order", Map.of("orderId", 8), "取消服务订单 #8", true);
        when(executor.prepare(eq("cancel_service_order"), anyMap(), eq(17)))
                .thenThrow(new IllegalArgumentException("订单状态已变更"));

        AgentActionService.ActionView pending = service.createPending(17, plan);
        AgentActionService.ActionExecution result = service.confirm(17, pending.confirmationToken());

        assertFalse(result.success());
        assertEquals(AgentActionService.FAILED, result.action().status());
        assertNull(mapper.selectById(pending.id()).getArgumentsJson());
    }

    @Test
    void workBuddyCallsAreVisibleWithoutPersistingCredentialsOrArguments() {
        AgentActionService service = new AgentActionService(
                mapper, mock(ToolExecutor.class), jsonMapper, Duration.ofMinutes(10), 20);

        service.recordIntegration("cecsms_list_services", true, "3 services returned");

        var history = service.integrationHistory(10);
        assertEquals(1, history.size());
        assertEquals(-1, history.getFirst().userId());
        assertEquals("mcp:cecsms_list_services", history.getFirst().toolName());
        assertEquals(AgentActionService.SUCCEEDED, history.getFirst().status());
        AgentAction stored = mapper.selectById(history.getFirst().id());
        assertNull(stored.getArgumentsJson());
        assertNull(stored.getConfirmationToken());
    }
}
