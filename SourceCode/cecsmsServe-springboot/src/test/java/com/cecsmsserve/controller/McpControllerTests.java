package com.cecsmsserve.controller;

import com.cecsmsserve.service.AgentActionService;
import com.cecsmsserve.service.AgentRunService;
import com.cecsmsserve.service.AiProviderService;
import com.cecsmsserve.service.KnowledgeBaseService;
import com.cecsmsserve.service.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class McpControllerTests {

    @Test
    void refusesMissingCredential() {
        McpController controller = controller("secret-value");
        ResponseEntity<Object> response = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"), null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsOnlySixReadOnlyTools() {
        McpController controller = controller("secret-value");
        ResponseEntity<Object> response = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list"),
                "secret-value", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(6, tools.size());
        assertTrue(tools.stream().allMatch(tool -> {
            Map<String, Object> annotations = (Map<String, Object>) tool.get("annotations");
            return Boolean.TRUE.equals(annotations.get("readOnlyHint"))
                    && Boolean.FALSE.equals(annotations.get("destructiveHint"));
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeReportsOnlyTheSupportedProtocolAndRejectsInvalidJsonRpc() {
        McpController controller = controller("secret-value");
        ResponseEntity<Object> initialized = controller.handle(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
                        "params", Map.of("protocolVersion", "2099-01-01")),
                "secret-value", null);
        Map<String, Object> initBody = (Map<String, Object>) initialized.getBody();
        Map<String, Object> result = (Map<String, Object>) initBody.get("result");
        assertEquals("2025-03-26", result.get("protocolVersion"));

        ResponseEntity<Object> invalid = controller.handle(
                Map.of("jsonrpc", "1.0", "id", 2, "method", "ping"),
                "secret-value", null);
        Map<String, Object> invalidBody = (Map<String, Object>) invalid.getBody();
        Map<String, Object> error = (Map<String, Object>) invalidBody.get("error");
        assertEquals(-32600, error.get("code"));
    }

    private McpController controller(String key) {
        return new McpController(
                key,
                mock(ToolExecutor.class),
                mock(AgentActionService.class),
                mock(KnowledgeBaseService.class),
                mock(AgentRunService.class),
                mock(AiProviderService.class),
                JsonMapper.builder().build());
    }
}
