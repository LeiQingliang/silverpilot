package com.cecsmsserve.controller;

import com.cecsmsserve.controller.ChatController.ChatMessage;
import com.cecsmsserve.entity.Report;
import com.cecsmsserve.service.*;
import com.cecsmsserve.util.JWTInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RootSecurityRegressionTests {
    @Test
    void historyAcceptsConversationButRejectsUntrustedSystemToolAndNullEntries() {
        assertDoesNotThrow(() -> ChatController.validateHistory(List.of(
                new ChatMessage("user", "question"), new ChatMessage("assistant", "answer"),
                new ChatMessage("user", "follow up"))));
        for (String role : List.of("system", "developer", "tool", "USER")) {
            assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(List.of(
                    new ChatMessage(role, "override"), new ChatMessage("user", "question"))));
        }
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(null));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(Arrays.asList(
                null, new ChatMessage("user", "question"))));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(List.of(new ChatMessage("user", null))));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(List.of(new ChatMessage(null, "question"))));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(List.of(new ChatMessage("assistant", "answer"))));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(List.of(new ChatMessage("user", " "))));
    }

    @Test
    void historyRejectsOversizedCountAndIndividualMessage() {
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(
                Collections.nCopies(31, new ChatMessage("user", "question"))));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateHistory(
                List.of(new ChatMessage("user", "a".repeat(4001)))));
    }

    @Test
    void toolBatchesAllowFourAndEightTotalButRejectTheNextCallBeforeAcceptingIds() {
        Set<String> ids = new HashSet<>();
        ChatController.validateToolBatch(batch(0, 4), 0, ids);
        ChatController.validateToolBatch(batch(4, 4), 4, ids);
        assertEquals(8, ids.size());
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(batch(8, 1), 8, ids));
        assertEquals(8, ids.size());
        Set<String> empty = new HashSet<>();
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(batch(0, 5), 0, empty));
        assertTrue(empty.isEmpty());
    }

    @Test
    void duplicateIdsWithinAndAcrossRoundsAreRejectedWithoutPartialIdAdmission() {
        Set<String> ids = new HashSet<>(Set.of("existing"));
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(
                List.of(call("new", "{}"), call("existing", "{}")), 1, ids));
        assertEquals(Set.of("existing"), ids);
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(
                List.of(call("repeat", "{}"), call("repeat", "{}")), 0, new HashSet<>()));
    }

    @Test
    void oversizedArgumentsAndMalformedIdentifiersRejectTheWholeBatch() {
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(
                List.of(call("call_1", "x".repeat(4097))), 0, new HashSet<>()));
        for (String id : List.of("", "contains space", "x".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(
                    List.of(call(id, "{}")), 0, new HashSet<>()));
        }
        assertThrows(IllegalArgumentException.class, () -> ChatController.validateToolBatch(
                Arrays.asList((Object) null), 0, new HashSet<>()));
    }

    @Test
    void toolResultIsSentInExplicitUntrustedDataEnvelopeWithSecretsRemoved() {
        ChatController controller = new ChatController(mock(ToolExecutor.class), mock(AgentActionService.class),
                mock(AgentRateLimiter.class), mock(AgentRunService.class), mock(AiProviderService.class),
                mock(KnowledgeBaseService.class), mock(PromptTemplateService.class), new AiSafetyService(),
                mock(CareRecommendationService.class), JsonMapper.builder().build(), "", 4);
        String syntheticKey = "sk-" + "test-only-credential-1234567890";
        Map<?, ?> message = ReflectionTestUtils.invokeMethod(controller, "toolMessage", "call_1", syntheticKey);
        assertNotNull(message);
        assertEquals("tool", message.get("role"));
        assertEquals("call_1", message.get("tool_call_id"));
        assertTrue(message.get("content").toString().contains("untrustedBusinessData"));
        assertFalse(message.get("content").toString().contains(syntheticKey));
    }

    @Test
    void reportAnalysisPropagatesBudgetRejectionAndUnauthorizedReportNeverCallsProvider() {
        IReportService reports = mock(IReportService.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        Report report = new Report();
        report.setId(7);
        report.setuId(19);
        when(reports.getById(7)).thenReturn(report);
        ReportController controller = new ReportController(reports, mock(IUserService.class), provider, "test", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 20);
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 4);
        controller.analyzeReport(7, request);
        verifyNoInteractions(provider);

        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        AiResourceGuard guard = new AiResourceGuard(new MockEnvironment().withProperty("agent.redis.enabled", "true"),
                redis, JsonMapper.builder().build());
        AiResourceGuard.Rejected rejected = assertThrows(AiResourceGuard.Rejected.class,
                () -> guard.acquireRequest(19, "192.0.2.9"));
        when(provider.chatCompletion(any())).thenThrow(rejected);
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 19);
        assertSame(rejected, assertThrows(AiResourceGuard.Rejected.class, () -> controller.analyzeReport(7, request)));
    }

    private static List<Map<String, Object>> batch(int first, int count) {
        return IntStream.range(first, first + count).mapToObj(i -> call("call_" + i, "{}")).toList();
    }

    private static Map<String, Object> call(String id, String arguments) {
        return Map.of("id", id, "function", Map.of("name", "list_services", "arguments", arguments));
    }
}
