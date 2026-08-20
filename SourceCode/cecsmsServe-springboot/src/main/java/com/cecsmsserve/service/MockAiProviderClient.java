package com.cecsmsserve.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit developer-only deterministic adapter. It never presents itself as a
 * real model and is enabled only through CECSMS_AI_MOCK_ENABLED.
 */
@Service
public class MockAiProviderClient {

    public static final String MODEL = "silverpilot-deterministic-mock-v1";
    private final JsonMapper jsonMapper;

    public MockAiProviderClient(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Map<String, Object> chatCompletion(Map<String, Object> body) {
        List<?> messages = body.get("messages") instanceof List<?> list ? list : List.of();
        Map<String, Object> message = responseMessage(messages);
        int inputChars = messages.stream().mapToInt(value -> String.valueOf(value).length()).sum();
        int outputChars = String.valueOf(message).length();
        return Map.of(
                "model", MODEL,
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop", "message", message)),
                "usage", Map.of(
                        "prompt_tokens", Math.max(1, inputChars / 4),
                        "completion_tokens", Math.max(1, outputChars / 4),
                        "total_tokens", Math.max(2, (inputChars + outputChars) / 4)));
    }

    private Map<String, Object> responseMessage(List<?> messages) {
        String toolResult = lastContent(messages, "tool");
        if (!toolResult.isBlank()) {
            return Map.of(
                    "role", "assistant",
                    "content", "【Mock 开发模式｜未调用外部模型】\n已完成本地工具路由，以下内容来自真实业务工具：\n"
                            + truncate(toolResult, 1800));
        }

        String userText = lastContent(messages, "user");
        ToolChoice choice = chooseReadTool(userText);
        if (choice != null) {
            Map<String, Object> function = Map.of(
                    "name", choice.name(),
                    "arguments", writeJson(choice.arguments()));
            Map<String, Object> toolCall = Map.of(
                    "id", "mock_" + UUID.randomUUID().toString().replace("-", ""),
                    "type", "function",
                    "function", function);
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", "");
            message.put("tool_calls", List.of(toolCall));
            return message;
        }

        return Map.of(
                "role", "assistant",
                "content", "【Mock 开发模式｜未调用外部模型】当前仅验证界面、路由和安全降级链路。"
                        + "查询活动、服务、菜谱、健康报告或知识库时仍会调用真实本地工具；复杂规划与写操作请配置 DeepSeek 或豆包。");
    }

    private ToolChoice chooseReadTool(String userText) {
        String text = userText == null ? "" : userText;
        if (text.contains("知识") || text.contains("SOP") || text.contains("写操作安全")
                || text.contains("未经确认") || text.contains("人工确认")) {
            return new ToolChoice("search_knowledge_base", Map.of("query", truncate(text, 200)));
        }
        if (text.contains("健康报告") || text.contains("健康档案")) {
            return new ToolChoice("my_health_reports", Map.of());
        }
        if (text.contains("活动")) return new ToolChoice("list_available_activities", Map.of());
        if (text.contains("菜谱") || text.contains("助餐")) {
            return new ToolChoice("list_recipes", Map.of());
        }
        if (text.contains("服务订单")) return new ToolChoice("my_service_orders", Map.of());
        if (text.contains("服务") && !text.contains("取消")) {
            return new ToolChoice("list_services", Map.of());
        }
        return null;
    }

    private String lastContent(List<?> messages, String role) {
        List<Object> values = new ArrayList<>(messages);
        for (int index = values.size() - 1; index >= 0; index--) {
            if (values.get(index) instanceof Map<?, ?> message
                    && role.equals(message.get("role"))
                    && message.get("content") instanceof String content) {
                return content;
            }
        }
        return "";
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    private record ToolChoice(String name, Map<String, Object> arguments) { }
}
