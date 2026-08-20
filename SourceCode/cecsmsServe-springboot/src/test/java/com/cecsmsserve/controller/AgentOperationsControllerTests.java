package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentActionService;
import com.cecsmsserve.service.AgentRateLimiter;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.service.AiProviderService;
import com.cecsmsserve.service.KnowledgeBaseService;
import com.cecsmsserve.service.PromptTemplateService;
import com.cecsmsserve.util.JWTInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOperationsControllerTests {

    @Test
    void rejectsNonAdministrator() {
        AgentOperationsController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 2);

        ResponseEntity<?> response = controller.overview(7, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void returnsSecretFreeOverviewForAdministrator() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentActionService actions = mock(AgentActionService.class);
        AiProviderService providers = mock(AiProviderService.class);
        KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        AgentRateLimiter limiter = mock(AgentRateLimiter.class);
        when(providers.statuses()).thenReturn(List.of());
        when(actions.integrationHistory(12)).thenReturn(List.of());
        when(actions.adminHistory("search_knowledge_base", 12)).thenReturn(List.of());
        when(actions.adminHistory(null, 12)).thenReturn(List.of());
        AgentOperationsController controller = new AgentOperationsController(
                runs, actions, providers, knowledge, prompts, limiter, "configured-but-not-returned");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 1);

        ResponseEntity<?> response = controller.overview(7, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String serializedShape = String.valueOf(response.getBody());
        assertEquals(false, serializedShape.contains("configured-but-not-returned"));
    }

    private AgentOperationsController controller() {
        return new AgentOperationsController(
                mock(AgentRunService.class),
                mock(AgentActionService.class),
                mock(AiProviderService.class),
                mock(KnowledgeBaseService.class),
                mock(PromptTemplateService.class),
                mock(AgentRateLimiter.class),
                "");
    }
}
