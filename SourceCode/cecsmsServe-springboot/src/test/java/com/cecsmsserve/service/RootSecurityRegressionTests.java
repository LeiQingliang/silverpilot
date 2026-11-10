package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cecsmsserve.entity.AgentAction;
import com.cecsmsserve.mapper.AgentActionMapper;
import com.cecsmsserve.util.DemoAccessPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RootSecurityRegressionTests {
    private final IActivityService activities = mock(IActivityService.class);
    private final IUserActivityService memberships = mock(IUserActivityService.class);
    private final IServiceTypeService services = mock(IServiceTypeService.class);
    private final IServiceOrderService orders = mock(IServiceOrderService.class);
    private final IReportService reports = mock(IReportService.class);
    private final IRecipeService recipes = mock(IRecipeService.class);
    private final IRecipeOrderService meals = mock(IRecipeOrderService.class);
    private final KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
    private final ToolExecutor executor = new ToolExecutor(activities, memberships, services, orders, reports,
            recipes, meals, knowledge, new DemoAccessPolicy(true, "1,17"));

    @Test
    void bothDemoIdentitiesCannotPrepareOrExecuteAnyWriteEvenWithForgedPlan() {
        for (int id : List.of(1, 17)) {
            for (String tool : List.of("join_activity", "book_service", "cancel_service_order", "book_recipe", "cancel_recipe_order")) {
                assertThrows(IllegalArgumentException.class, () -> executor.prepare(tool, Map.of(), id));
                ToolExecutor.ToolResult result = executor.execute(new ToolExecutor.ToolPlan(tool, Map.of(), "forged", false), id);
                assertFalse(result.success());
                assertTrue(result.content().contains("共享演示账号"));
            }
        }
        noBusinessCalls();
    }

    @Test
    void unknownFieldsAndForgedIdentityAreRejectedBeforeAnyServiceCallAtBothBoundaries() {
        for (String key : List.of("userId", "uId", "roleId", "unexpected")) {
            Map<String, Object> args = Map.of(key, 1);
            assertThrows(IllegalArgumentException.class, () -> executor.prepare("my_health_reports", args, 19));
            assertFalse(executor.execute(new ToolExecutor.ToolPlan("my_health_reports", args, "forged", false), 19).success());
        }
        for (Integer id : java.util.Arrays.asList(null, 0, -1)) {
            assertThrows(IllegalArgumentException.class, () -> executor.prepare("my_health_reports", Map.of(), id));
            assertFalse(executor.execute(new ToolExecutor.ToolPlan("my_health_reports", Map.of(), "forged", false), id).success());
        }
        noBusinessCalls();
    }

    @Test
    void decimalOverflowNonfiniteAndNonNumericIdsCannotCoerceToAnotherRecord() {
        for (Object value : List.of(new BigDecimal("1.5"), 1.5d, 2147483648L,
                new BigInteger("18446744073709551617"), "2147483648", "1.5", Double.NaN,
                Double.POSITIVE_INFINITY, true, Map.of("id", 1), 0, -1)) {
            Map<String, Object> args = Map.of("reportId", value);
            assertThrows(IllegalArgumentException.class, () -> executor.prepare("get_health_report_detail", args, 19),
                    () -> "Unexpected accepted type: " + value.getClass().getSimpleName());
            assertFalse(executor.execute(new ToolExecutor.ToolPlan("get_health_report_detail", args, "forged", false), 19).success());
        }
        noBusinessCalls();
    }

    @Test
    void textArgumentsCannotSmuggleObjectsAndDemoReadPreparationRemainsAvailable() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.prepare("list_recipes", Map.of("keyword", Map.of("userId", 1)), 19));
        assertThrows(IllegalArgumentException.class,
                () -> executor.prepare("search_knowledge_base", Map.of("query", List.of("text")), 19));
        assertFalse(executor.prepare("list_services", Map.of(), 1).requiresConfirmation());
        assertFalse(executor.prepare("my_health_reports", Map.of(), 17).requiresConfirmation());
        assertFalse(executor.isReadOnly(19));
        noBusinessCalls();
    }

    @Test
    void readonlyHistoryDoesNotExpireOrOtherwiseUpdateBusinessRows() {
        AgentActionMapper mapper = mock(AgentActionMapper.class);
        when(mapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<AgentAction>>any())).thenReturn(List.of());
        AgentActionService actions = new AgentActionService(mapper, executor, JsonMapper.builder().build(), Duration.ofMinutes(10), 20);
        assertEquals(List.of(), actions.readOnlyHistory(1, 500));
        verify(mapper).selectList(org.mockito.ArgumentMatchers.<Wrapper<AgentAction>>any());
        verifyNoMoreInteractions(mapper);
        assertThrows(IllegalArgumentException.class, () -> actions.readOnlyHistory(0, 10));
    }

    @Test
    void toolOutputRemovesJsonCredentialsAndPrivateKeyMaterial() {
        AiSafetyService safety = new AiSafetyService();
        String secret = "synthetic-test-credential-123456";
        for (String key : List.of("password", "api_key", "access_token", "client_secret")) {
            String cleaned = safety.sanitizeToolResult("{\"" + key + "\":\"" + secret + "\",\"data\":\"useful\"}");
            assertFalse(cleaned.contains(secret), key);
            assertTrue(cleaned.contains("凭据已隐藏"), key);
        }
        assertEquals("", safety.sanitizeToolResult(null));
        String privateKey = "-----BEGIN " + "PRIVATE KEY-----\nsynthetic key material";
        assertFalse(safety.sanitizeToolResult(privateKey).contains("synthetic key material"));
    }

    @Test
    void redactionPrecedesTruncationEvenWhenIdentifiersCrossTheOutputBoundary() {
        AiSafetyService safety = new AiSafetyService();
        String padding = " ".repeat(5994);
        String phone = "138" + "1234" + "5678";
        String identity = "990000" + "19500101" + "1234";
        String key = "sk-" + "synthetic-credential-1234567890";
        assertFalse(safety.sanitizeToolResult(padding + phone + " trailing").contains("138123"));
        assertTrue(safety.sanitizeToolResult(" ".repeat(5992) + identity + " trailing").contains("990000**"));
        assertFalse(safety.sanitizeToolResult(padding + key + " trailing").contains("sk-syn"));
        String longResult = safety.sanitizeToolResult("a".repeat(6100));
        assertTrue(longResult.startsWith("a".repeat(6000)));
        assertTrue(longResult.endsWith("[结果已截断]"));
        assertTrue(longResult.length() < 6030);
    }

    private void noBusinessCalls() {
        verifyNoInteractions(activities, memberships, services, orders, reports, recipes, meals, knowledge);
    }
}
