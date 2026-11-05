package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts SilverPilot's internal chat/tool contract to LongCat Responses API.
 * Business tools still run in ToolExecutor; this client never executes a tool.
 */
@Service
public class LongCatClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };
    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final Duration retryBackoff;

    @Autowired
    public LongCatClient(
            @Value("${longcat.api.url:https://api.longcat.chat/openai/v1/responses}") String apiUrl,
            @Value("${longcat.api.key:}") String apiKey,
            @Value("${longcat.api.model:LongCat-2.5}") String model,
            @Value("${longcat.api.connect-timeout:5s}") Duration connectTimeout,
            @Value("${longcat.api.read-timeout:90s}") Duration readTimeout,
            @Value("${agent.ai.retry.max-attempts:2}") int maxAttempts,
            @Value("${agent.ai.retry.backoff:350ms}") Duration retryBackoff) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.apiUrl = value(apiUrl);
        this.apiKey = value(apiKey);
        this.model = value(model);
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 4));
        this.retryBackoff = retryBackoff.isNegative() ? Duration.ZERO : retryBackoff;
    }

    public boolean isConfigured() {
        return !apiUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    public String model() {
        return model;
    }

    public Map<String, Object> chatCompletion(Map<String, Object> body) {
        if (!isConfigured()) {
            throw new AiProviderException("LongCat 尚未配置，请设置 LONGCAT_API_KEY 与 LONGCAT_MODEL", 503);
        }
        URI endpoint = configuredEndpoint();
        Map<String, Object> request = responsesRequest(body);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map<String, Object> response = restClient.post().uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .body(request).retrieve().body(RESPONSE_TYPE);
                return normalizeResponse(response);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new AiProviderException("LongCat 认证失败，请检查本机 API Key 与账号权限", 503);
                }
                if ((status == 429 || status >= 500) && attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                if (status == 429) {
                    throw new AiProviderException("LongCat 请求过于频繁，请稍后重试", 503);
                }
                // Never expose the upstream response body or credentials through an exception.
                throw new AiProviderException("LongCat 暂时不可用（HTTP " + status + "）", 502);
            } catch (RestClientException ex) {
                if (attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                throw new AiProviderException("无法连接 LongCat，请检查网络后重试", 503);
            } catch (IllegalArgumentException ex) {
                // HTTP builders may include a complete header or URL in their error message.
                // Do not allow configuration values or their exception causes to reach the controller.
                throw configurationError();
            }
        }
        throw new AiProviderException("LongCat 暂时不可用", 503);
    }

    private URI configuredEndpoint() {
        // API keys are opaque printable ASCII tokens, never header whitespace or control characters.
        if (apiKey.chars().anyMatch(character -> character <= 32 || character >= 127)) {
            throw configurationError();
        }
        try {
            URI endpoint = URI.create(apiUrl);
            if ((!"https".equalsIgnoreCase(endpoint.getScheme()) && !"http".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getHost().isBlank()
                    || endpoint.getRawUserInfo() != null || endpoint.getFragment() != null
                    || endpoint.getPort() > 65535) {
                throw configurationError();
            }
            return endpoint;
        } catch (IllegalArgumentException ex) {
            throw configurationError();
        }
    }

    private AiProviderException configurationError() {
        return new AiProviderException("LongCat 本机配置格式无效，请检查 API 地址和密钥格式", 503);
    }

    private Map<String, Object> responsesRequest(Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("stream", false);
        request.put("store", false);
        request.put("reasoning", Map.of("effort", "none"));
        request.put("temperature", 0.7);
        request.put("max_output_tokens", body.getOrDefault("max_tokens", 1800));
        List<Map<String, Object>> input = new ArrayList<>();
        if (!(body.get("messages") instanceof List<?> messages)) {
            throw new IllegalArgumentException("LongCat 消息格式无效");
        }
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> message)) {
                throw new IllegalArgumentException("LongCat 消息格式无效");
            }
            String role = requiredText(message, "role");
            if ("tool".equals(role)) {
                input.add(Map.of("type", "function_call_output",
                        "call_id", requiredText(message, "tool_call_id"),
                        "output", textContent(message)));
                continue;
            }
            if (!List.of("system", "developer", "user", "assistant").contains(role)) {
                throw new IllegalArgumentException("LongCat 消息角色无效");
            }
            String content = textContent(message);
            if (!content.isBlank()) input.add(Map.of("role", role, "content", content));
            if (message.get("tool_calls") instanceof List<?> calls) {
                if (!"assistant".equals(role)) throw new IllegalArgumentException("工具调用必须来自模型");
                for (Object call : calls) {
                    if (!(call instanceof Map<?, ?> toolCall)
                            || !(toolCall.get("function") instanceof Map<?, ?> function)) {
                        throw new IllegalArgumentException("LongCat 工具调用格式无效");
                    }
                    input.add(Map.of("type", "function_call",
                            "call_id", requiredText(toolCall, "id"),
                            "name", requiredText(function, "name"),
                            "arguments", requiredText(function, "arguments")));
                }
            }
        }
        request.put("input", input);
        if (body.get("tools") instanceof List<?> tools && !tools.isEmpty()) {
            List<Map<String, Object>> definitions = new ArrayList<>();
            for (Object tool : tools) {
                if (!(tool instanceof Map<?, ?> definition)
                        || !"function".equals(definition.get("type"))
                        || !(definition.get("function") instanceof Map<?, ?> function)
                        || !(function.get("parameters") instanceof Map<?, ?> parameters)) {
                    throw new IllegalArgumentException("LongCat 工具定义格式无效");
                }
                Map<String, Object> flattened = new LinkedHashMap<>();
                flattened.put("type", "function");
                flattened.put("name", requiredText(function, "name"));
                flattened.put("parameters", parameters);
                if (function.get("description") instanceof String description) {
                    flattened.put("description", description);
                }
                // Existing optional parameters are validated by the backend, not strict JSON Schema mode.
                flattened.put("strict", false);
                definitions.add(flattened);
            }
            request.put("tools", definitions);
            request.put("parallel_tool_calls", false);
        }
        if (body.get("response_format") instanceof Map<?, ?> format
                && "json_object".equals(format.get("type"))) {
            request.put("text", Map.of("format", Map.of("type", "json_object")));
        }
        return request;
    }

    private Map<String, Object> normalizeResponse(Map<String, Object> response) {
        if (response == null || !"completed".equals(response.get("status"))) {
            // Incomplete tool arguments must never reach the business executor.
            throw new AiProviderException("LongCat 未完整生成结果，请缩短任务后重试", 502);
        }
        if (!(response.get("output") instanceof List<?> output)) {
            throw new AiProviderException("LongCat 返回格式异常", 502);
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Object item : output) {
            if (!(item instanceof Map<?, ?> value)) throw invalidResponse();
            if ("function_call".equals(value.get("type"))) {
                calls.add(Map.of("id", responseText(value, "call_id"), "type", "function",
                        "function", Map.of("name", responseText(value, "name"),
                                "arguments", responseText(value, "arguments"))));
            } else if ("message".equals(value.get("type"))) {
                if (!(value.get("content") instanceof List<?> content)) throw invalidResponse();
                for (Object block : content) {
                    if (block instanceof Map<?, ?> part && "output_text".equals(part.get("type"))
                            && part.get("text") instanceof String segment) {
                        text.append(segment);
                    }
                }
            } else if (!"reasoning".equals(value.get("type"))) {
                throw invalidResponse();
            }
            // Reasoning is intentionally neither displayed, persisted nor replayed.
        }
        if (calls.isEmpty() && text.toString().isBlank()) throw invalidResponse();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", text.toString());
        if (!calls.isEmpty()) message.put("tool_calls", calls);
        Map<?, ?> usage = response.get("usage") instanceof Map<?, ?> found ? found : Map.of();
        int inputTokens = tokens(usage.get("input_tokens"));
        int outputTokens = tokens(usage.get("output_tokens"));
        int totalTokens = (int) Math.min(Integer.MAX_VALUE,
                Math.max((long) inputTokens + outputTokens, tokens(usage.get("total_tokens"))));
        return Map.of("choices", List.of(Map.of("message", message)), "usage",
                Map.of("prompt_tokens", inputTokens, "completion_tokens", outputTokens,
                        "total_tokens", totalTokens));
    }

    private String textContent(Map<?, ?> message) {
        Object content = message.get("content");
        if (content == null) return "";
        if (content instanceof String text) return text;
        throw new IllegalArgumentException("当前 LongCat 通道仅启用文本，请使用豆包处理图片");
    }

    private String requiredText(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException("LongCat 请求字段无效：" + key);
    }

    private String responseText(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String text && !text.isBlank()) return text;
        throw invalidResponse();
    }

    private AiProviderException invalidResponse() {
        return new AiProviderException("LongCat 返回格式异常", 502);
    }

    private int tokens(Object value) {
        return value instanceof Number number
                ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, number.longValue())) : 0;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(Math.min(2_000, retryBackoff.toMillis() * (1L << (attempt - 1))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("LongCat 请求已中断", 503);
        }
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
