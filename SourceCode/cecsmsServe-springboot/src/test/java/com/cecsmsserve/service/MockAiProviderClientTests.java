package com.cecsmsserve.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAiProviderClientTests {

    @Test
    void clearlyLabelsMockModeAndStillRoutesReadOnlyBusinessTools() {
        MockAiProviderClient client = new MockAiProviderClient(JsonMapper.builder().build());
        Map<String, Object> first = client.chatCompletion(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "有哪些养老服务？"))));
        Map<?, ?> firstMessage = firstMessage(first);
        List<?> calls = (List<?>) firstMessage.get("tool_calls");
        Map<?, ?> call = (Map<?, ?>) calls.getFirst();
        Map<?, ?> function = (Map<?, ?>) call.get("function");
        assertEquals("list_services", function.get("name"));

        Map<String, Object> second = client.chatCompletion(Map.of(
                "messages", List.of(Map.of("role", "tool", "content", "服务ID 9：上门助浴"))));
        assertTrue(String.valueOf(firstMessage(second).get("content")).contains("Mock 开发模式"));
        assertTrue(String.valueOf(firstMessage(second).get("content")).contains("服务ID 9"));
    }

    @Test
    void routesHumanConfirmationPolicyQuestionsToApprovedKnowledge() {
        MockAiProviderClient client = new MockAiProviderClient(JsonMapper.builder().build());
        Map<String, Object> response = client.chatCompletion(Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "这个Agent怎样防止未经确认就下单？"))));

        Map<?, ?> message = firstMessage(response);
        List<?> calls = (List<?>) message.get("tool_calls");
        Map<?, ?> function = (Map<?, ?>) ((Map<?, ?>) calls.getFirst()).get("function");
        assertEquals("search_knowledge_base", function.get("name"));
    }

    private Map<?, ?> firstMessage(Map<String, Object> response) {
        List<?> choices = (List<?>) response.get("choices");
        return (Map<?, ?>) ((Map<?, ?>) choices.getFirst()).get("message");
    }
}
