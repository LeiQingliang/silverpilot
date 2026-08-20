package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiProviderService {

    public static final String AUTO = "auto";
    public static final String DEEPSEEK = "deepseek";
    public static final String DOUBAO = "doubao";
    public static final String MOCK = "mock";

    private final DeepSeekClient deepSeekClient;
    private final DoubaoClient doubaoClient;
    private final MockAiProviderClient mockClient;
    private final String deepSeekModel;
    private final boolean deepSeekThinking;
    private final boolean mockEnabled;
    private final int maxCompletionTokens;
    private final int maxTotalTokensPerRun;

    @Autowired
    public AiProviderService(
            DeepSeekClient deepSeekClient,
            DoubaoClient doubaoClient,
            MockAiProviderClient mockClient,
            @Value("${deepseek.api.model:deepseek-v4-flash}") String deepSeekModel,
            @Value("${deepseek.api.thinking-enabled:false}") boolean deepSeekThinking,
            @Value("${agent.ai.mock-enabled:false}") boolean mockEnabled,
            @Value("${agent.ai.max-completion-tokens:1800}") int maxCompletionTokens,
            @Value("${agent.ai.max-total-tokens-per-run:12000}") int maxTotalTokensPerRun) {
        this.deepSeekClient = deepSeekClient;
        this.doubaoClient = doubaoClient;
        this.mockClient = mockClient;
        this.deepSeekModel = deepSeekModel;
        this.deepSeekThinking = deepSeekThinking;
        this.mockEnabled = mockEnabled;
        this.maxCompletionTokens = Math.max(256, Math.min(maxCompletionTokens, 8_000));
        this.maxTotalTokensPerRun = Math.max(this.maxCompletionTokens, maxTotalTokensPerRun);
    }

    AiProviderService(
            DeepSeekClient deepSeekClient,
            DoubaoClient doubaoClient,
            String deepSeekModel,
            boolean deepSeekThinking) {
        this(deepSeekClient, doubaoClient, null, deepSeekModel, deepSeekThinking,
                false, 1800, 12000);
    }

    public ProviderSelection resolve(String requested, boolean needsVision) {
        String normalized = requested == null || requested.isBlank()
                ? AUTO : requested.trim().toLowerCase();
        if (!List.of(AUTO, DEEPSEEK, DOUBAO, MOCK).contains(normalized)) {
            throw new IllegalArgumentException("不支持的模型提供方：" + requested);
        }

        if (needsVision) {
            if (DEEPSEEK.equals(normalized) || MOCK.equals(normalized)) {
                throw new IllegalArgumentException("当前 DeepSeek 通道仅启用文本能力，请选择豆包多模态");
            }
            if (!doubaoClient.isConfigured()) {
                throw new AiProviderException(
                        "图片理解需要豆包多模态，请先设置 DOUBAO_API_KEY 与 DOUBAO_MODEL", 503);
            }
            return doubao();
        }

        if (DEEPSEEK.equals(normalized)) {
            if (!deepSeekClient.isConfigured()) {
                throw new AiProviderException("DeepSeek 尚未配置，请设置 DEEPSEEK_API_KEY", 503);
            }
            return deepSeek();
        }
        if (DOUBAO.equals(normalized)) {
            if (!doubaoClient.isConfigured()) {
                throw new AiProviderException(
                        "豆包尚未配置，请设置 DOUBAO_API_KEY 与 DOUBAO_MODEL", 503);
            }
            return doubao();
        }
        if (MOCK.equals(normalized)) {
            if (!mockEnabled || mockClient == null) {
                throw new AiProviderException("Mock 模式未启用，请设置 CECSMS_AI_MOCK_ENABLED=true", 503);
            }
            return mock();
        }
        if (deepSeekClient.isConfigured()) {
            return deepSeek();
        }
        if (doubaoClient.isConfigured()) {
            return doubao();
        }
        if (mockEnabled && mockClient != null) {
            return mock();
        }
        throw new AiProviderException("没有可用模型，请先配置 DeepSeek 或豆包", 503);
    }

    public Map<String, Object> chatCompletion(
            ProviderSelection provider, Map<String, Object> body) {
        return switch (provider.id()) {
            case DEEPSEEK -> deepSeekClient.chatCompletion(body);
            case DOUBAO -> doubaoClient.chatCompletion(body);
            case MOCK -> mockClient.chatCompletion(body);
            default -> throw new IllegalArgumentException("模型路由无效");
        };
    }

    public Map<String, Object> firstMessage(Map<String, Object> response) {
        return deepSeekClient.firstMessage(response);
    }

    public List<ProviderView> statuses() {
        return List.of(
                new ProviderView(DEEPSEEK, "DeepSeek", deepSeekModel,
                        deepSeekClient.isConfigured(), true, false, false,
                        deepSeekClient.isConfigured() ? "文本与业务工具在线" : "等待本机密钥"),
                new ProviderView(DOUBAO, "豆包·火山方舟", doubaoClient.model(),
                        doubaoClient.isConfigured(), true, true, false,
                        doubaoClient.isConfigured() ? "文本、图片与业务工具在线" : "等待 API Key 与模型 Endpoint ID"),
                new ProviderView(MOCK, "Mock · 本地开发", MockAiProviderClient.MODEL,
                        mockEnabled && mockClient != null, true, false, false,
                        mockEnabled ? "显式开发模式：本地工具可用，不调用外部模型" : "默认关闭")
        );
    }

    public Map<String, Object> decorateBody(
            ProviderSelection provider, List<Map<String, Object>> messages, List<Map<String, Object>> tools,
            Integer userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("stream", false);
        if (!provider.thinkingEnabled()) body.put("temperature", 0.2);
        body.put("max_tokens", maxCompletionTokens);
        body.put("user", "cecsms-user-" + userId);
        if (DEEPSEEK.equals(provider.id())) {
            body.put("thinking", Map.of("type", provider.thinkingEnabled() ? "enabled" : "disabled"));
            if (provider.thinkingEnabled()) {
                body.remove("tool_choice");
            }
            // Kept for compatibility with the currently verified DeepSeek endpoint.
            body.put("user_id", "cecsms-user-" + userId);
        }
        return body;
    }

    public Map<String, Object> decorateStructuredBody(
            ProviderSelection provider,
            List<Map<String, Object>> messages,
            Integer userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", 0.1);
        body.put("max_tokens", Math.min(maxCompletionTokens, 1400));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("user", "cecsms-user-" + userId);
        if (DEEPSEEK.equals(provider.id())) {
            body.put("thinking", Map.of("type", "disabled"));
            body.put("user_id", "cecsms-user-" + userId);
        }
        return body;
    }

    public TokenUsage usage(Map<String, Object> response) {
        if (!(response.get("usage") instanceof Map<?, ?> usage)) return TokenUsage.ZERO;
        return new TokenUsage(number(usage.get("prompt_tokens")),
                number(usage.get("completion_tokens")), number(usage.get("total_tokens")));
    }

    public String firstText(Map<String, Object> response) {
        Object content = firstMessage(response).get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            throw new AiProviderException("AI 服务未返回有效文本", 502);
        }
        return text.trim();
    }

    public int maxTotalTokensPerRun() {
        return maxTotalTokensPerRun;
    }

    private int number(Object value) {
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private ProviderSelection deepSeek() {
        return new ProviderSelection(DEEPSEEK, "DeepSeek", deepSeekModel, false, deepSeekThinking);
    }

    private ProviderSelection doubao() {
        return new ProviderSelection(DOUBAO, "豆包·火山方舟", doubaoClient.model(), true, false);
    }

    private ProviderSelection mock() {
        return new ProviderSelection(MOCK, "Mock · 本地开发", MockAiProviderClient.MODEL, false, false);
    }

    public record ProviderSelection(
            String id, String displayName, String model, boolean vision, boolean thinkingEnabled) { }

    public record ProviderView(
            String id,
            String name,
            String model,
            boolean configured,
            boolean text,
            boolean vision,
            boolean serverVoice,
            String statusText) { }

    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);
        public TokenUsage plus(TokenUsage other) {
            return new TokenUsage(promptTokens + other.promptTokens,
                    completionTokens + other.completionTokens,
                    totalTokens + other.totalTokens);
        }
    }
}
