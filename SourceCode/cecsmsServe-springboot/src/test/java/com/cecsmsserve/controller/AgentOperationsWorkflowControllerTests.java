package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentFeedbackService;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.util.JWTInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOperationsWorkflowControllerTests {
    @Test
    void allReadsAndWritesRequireAuthenticatedAdministrator() {
        var feedback = mock(AgentFeedbackService.class);
        var runs = mock(AgentRunService.class);
        var controller = new AgentOperationsWorkflowController(feedback, runs);
        var request = new MockHttpServletRequest();
        assertEquals(HttpStatus.UNAUTHORIZED, controller.overview(7, request).getStatusCode());
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 2);
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 7);
        assertEquals(HttpStatus.FORBIDDEN, controller.overview(7, request).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.export(7, request).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.create(
                new AgentOperationsWorkflowController.CreateFeedback("a".repeat(32), "TOOL_ERROR", "P1"), request).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.transition("a".repeat(32),
                new AgentOperationsWorkflowController.Transition(0, "TRIAGED", null, null, false), request).getStatusCode());
        verifyNoInteractions(feedback, runs);
    }

    @Test
    void reportExcludesRunUserAndEvidenceIdentifiers() {
        var feedback = mock(AgentFeedbackService.class);
        var runs = mock(AgentRunService.class);
        var view = new AgentRunService.AnalyticsView(7, 1, 100, 10, 1, 2, 100, 0, 0,
                Map.of("deepseek", 1L), Map.of("TEXT", 1L),
                List.of(new AgentRunService.RunView("private-run-id", 999, "deepseek", "model", "TEXT",
                        "COMPLETED", 10, 1, 1, false, 2, "v1", null)),
                1, 0, 10, 10, false, 5000, Map.of(), List.of());
        when(runs.systemAnalytics(7)).thenReturn(view);
        when(feedback.summary(7)).thenReturn(Map.of("total", 0));
        when(feedback.actionOutcomes(7)).thenReturn(Map.of("total", 0));
        var request = admin();
        var response = new AgentOperationsWorkflowController(feedback, runs).export(7, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = String.valueOf(response.getBody());
        assertFalse(body.contains("private-run-id"));
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("recentRuns"));
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    void exposesConcurrentEditAsConflict() {
        var feedback = mock(AgentFeedbackService.class);
        var runs = mock(AgentRunService.class);
        when(feedback.transition("a".repeat(32), 0, "TRIAGED", null, null, false, 1))
                .thenThrow(new AgentFeedbackService.Conflict("记录已更新"));
        var response = new AgentOperationsWorkflowController(feedback, runs).transition("a".repeat(32),
                new AgentOperationsWorkflowController.Transition(0, "TRIAGED", null, null, false), admin());
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    private MockHttpServletRequest admin() {
        var request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 1);
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 1);
        return request;
    }
}
