package com.cecsmsserve.service;

import com.cecsmsserve.controller.ChatController;
import com.cecsmsserve.util.JWTInterceptor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongCatClientTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void completesTwoTurnToolRoundTripWithFlattenedSchemaAndTokenUsage() throws IOException {
        List<Map<String, Object>> requests = new ArrayList<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(readBody(exchange));
            respond(exchange, 200, requests.size() == 1 ? """
                    {"status":"completed","output":[
                      {"type":"reasoning","content":[{"type":"reasoning_text","text":"private-thought"}]},
                      {"type":"function_call","call_id":"call-service-1","name":"list_services","arguments":"{}"}
                    ],"usage":{"input_tokens":12,"output_tokens":8,"total_tokens":20}}
                    """ : completedText("服务 11：助餐"));
        });
        AiProviderService service = service(client("test-api-key"));
        var provider = service.resolve("longcat", false);
        List<Map<String, Object>> messages = new ArrayList<>(List.of(
                Map.of("role", "system", "content", "Read real services using tools."),
                Map.of("role", "user", "content", "有哪些服务")));
        List<Map<String, Object>> tools = List.of(Map.of("type", "function", "function", Map.of(
                "name", "list_services", "description", "Read available services",
                "parameters", Map.of("type", "object", "properties", Map.of()))));
        Map<String, Object> first = service.chatCompletion(provider,
                service.decorateBody(provider, messages, tools, 17));
        Map<String, Object> firstMessage = service.firstMessage(first);
        assertTrue(firstMessage.containsKey("tool_calls"));
        assertEquals(new AiProviderService.TokenUsage(12, 8, 20), service.usage(first));
        assertFalse(JSON.writeValueAsString(first).contains("private-thought"));
        messages.add(firstMessage);
        messages.add(Map.of("role", "tool", "tool_call_id", "call-service-1",
                "content", "[{\"id\":11,\"name\":\"助餐\"}]"));
        Map<String, Object> second = service.chatCompletion(provider,
                service.decorateBody(provider, messages, tools, 17));

        assertEquals("服务 11：助餐", service.firstText(second));
        assertEquals("Bearer test-api-key", authorization.get());
        Map<String, Object> firstRequest = requests.getFirst();
        assertEquals("LongCat-2.5", firstRequest.get("model"));
        assertEquals(false, firstRequest.get("store"));
        assertEquals(false, firstRequest.get("parallel_tool_calls"));
        assertEquals(Map.of("effort", "none"), firstRequest.get("reasoning"));
        assertEquals(0.7, firstRequest.get("temperature"));
        assertEquals(1800, firstRequest.get("max_output_tokens"));
        assertFalse(firstRequest.containsKey("messages"));
        assertFalse(firstRequest.containsKey("user"));
        Map<?, ?> definition = (Map<?, ?>) ((List<?>) firstRequest.get("tools")).getFirst();
        assertEquals("list_services", definition.get("name"));
        assertFalse(definition.containsKey("function"));
        List<?> replay = (List<?>) requests.getLast().get("input");
        assertEquals(Map.of("type", "function_call", "call_id", "call-service-1",
                "name", "list_services", "arguments", "{}"), replay.get(2));
        assertEquals(Map.of("type", "function_call_output", "call_id", "call-service-1",
                "output", "[{\"id\":11,\"name\":\"助餐\"}]"), replay.get(3));
        assertFalse(JSON.writeValueAsString(requests).contains("private-thought"));
    }

    @Test
    void mapsStructuredJsonModeWithoutForwardingUnsupportedChatFields() throws IOException {
        AtomicReference<Map<String, Object>> request = new AtomicReference<>();
        startServer(exchange -> {
            request.set(readBody(exchange));
            respond(exchange, 200, completedText("{\"urgency\":\"low\",\"recommendations\":[]}"));
        });
        AiProviderService service = service(client("test-api-key"));
        var provider = service.resolve("longcat", false);
        var result = service.chatCompletion(provider, service.decorateStructuredBody(provider,
                List.of(Map.of("role", "user", "content", "请以 JSON 输出建议")), 17));

        assertEquals(Map.of("format", Map.of("type", "json_object")), request.get().get("text"));
        assertEquals(1400, request.get().get("max_output_tokens"));
        assertFalse(request.get().containsKey("response_format"));
        assertFalse(request.get().containsKey("tools"));
        assertEquals("low", JSON.readValue(service.firstText(result), MAP_TYPE).get("urgency"));
    }

    @Test
    void modelWriteToolOnlyCreatesPendingActionThroughExistingController() throws IOException {
        startServer(exchange -> {
            readBody(exchange);
            respond(exchange, 200, JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(
                    Map.of("type", "function_call", "call_id", "write-1", "name", "book_service",
                            "arguments", "{\"serviceId\":11}")),
                    "usage", Map.of("input_tokens", 10, "output_tokens", 10, "total_tokens", 20))));
        });
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentActionService actions = mock(AgentActionService.class);
        AgentRateLimiter limiter = mock(AgentRateLimiter.class);
        AgentRunService runs = mock(AgentRunService.class);
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        AiSafetyService safety = mock(AiSafetyService.class);
        when(limiter.acquire(17)).thenReturn(new AgentRateLimiter.Decision(true, 0));
        when(prompts.metadata(PromptTemplateService.AGENT))
                .thenReturn(new PromptTemplateService.PromptMetadata("agent", "test-v1", "test-fingerprint"));
        when(prompts.renderAgent(any())).thenReturn("Use tools and ask the user to confirm writes.");
        when(safety.redactForModel(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var plan = new ToolExecutor.ToolPlan("book_service", Map.of("serviceId", 11), "预约助餐", true);
        when(executor.prepare("book_service", Map.of("serviceId", 11), 17)).thenReturn(plan);
        var pending = new AgentActionService.ActionView(1L, "confirmation-token", "book_service", "预约助餐",
                "PENDING", null, LocalDateTime.now().plusMinutes(10), LocalDateTime.now(), null);
        when(actions.createPending(17, plan)).thenReturn(pending);
        ChatController controller = new ChatController(executor, actions, limiter, runs, service(client("test-key")),
                mock(KnowledgeBaseService.class), prompts, safety, mock(CareRecommendationService.class), JSON, "", 8);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 17);

        var response = controller.chat(new ChatController.ChatRequest(
                List.of(new ChatController.ChatMessage("user", "预约服务11")), "longcat", List.of()), http);

        assertEquals(200, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(pending, body.pendingAction());
        assertEquals(AgentRunService.WAITING_CONFIRMATION, body.run().status());
        assertEquals("longcat", body.run().provider());
        verify(actions).createPending(17, plan);
        verify(executor, never()).execute(any(), any());
        verify(actions, never()).confirm(any(), anyString());
    }

    @Test
    void refusesIncompleteToolArgumentsAndMalformedSuccessfulResponses() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            readBody(exchange);
            respond(exchange, 200, calls.incrementAndGet() == 1
                    ? JSON.writeValueAsString(Map.of("status", "incomplete", "output", List.of(
                            Map.of("type", "function_call", "call_id", "bad-call", "name", "book_service",
                                    "arguments", "{\"serviceId\":"))))
                    : "{\"status\":\"completed\",\"output\":[{\"type\":\"function_call\",\"name\":\"book_service\"}]}");
        });
        assertEquals(502, assertThrows(AiProviderException.class,
                () -> client("test-key").chatCompletion(simpleRequest())).getHttpStatus());
        assertEquals(502, assertThrows(AiProviderException.class,
                () -> client("test-key").chatCompletion(simpleRequest())).getHttpStatus());
        assertEquals(2, calls.get(), "Malformed HTTP 200 responses must not be retried");
    }

    @Test
    void retriesTransientRateLimitButHidesAuthenticationResponseDetails() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        startServer(exchange -> {
            readBody(exchange);
            int call = calls.incrementAndGet();
            respond(exchange, call == 1 ? 429 : call == 2 ? 200 : 401,
                    call == 2 ? completedText("已读取") : "{\"error\":\"secret-upstream-detail\"}");
        });
        AiProviderService service = service(client("test-key"));
        assertEquals("已读取", service.firstText(client("test-key").chatCompletion(simpleRequest())));
        assertEquals(2, calls.get());
        AiProviderException failure = assertThrows(AiProviderException.class,
                () -> client("invalid-key").chatCompletion(simpleRequest()));
        assertEquals(503, failure.getHttpStatus());
        assertFalse(failure.getMessage().contains("secret-upstream-detail"));
        assertEquals(3, calls.get(), "Authentication failures must not be retried");
    }

    @Test
    void rejectsMissingCredentialsAndImageInputWithoutNetworkAccess() {
        LongCatClient unconfigured = new LongCatClient("http://127.0.0.1:1/responses", " ", "LongCat-2.5",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO);
        assertFalse(unconfigured.isConfigured());
        assertEquals(503, assertThrows(AiProviderException.class,
                () -> unconfigured.chatCompletion(simpleRequest())).getHttpStatus());
        LongCatClient textClient = new LongCatClient("http://127.0.0.1:1/responses", "test-key", "LongCat-2.5",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> textClient.chatCompletion(Map.of("messages",
                List.of(Map.of("role", "user", "content", List.of(Map.of("type", "image_url")))))));
    }

    @Test
    void invalidConfigurationNeverExposesCredentialsOrAttemptsHttp() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            readBody(exchange);
            respond(exchange, 200, completedText("should not be requested"));
        });
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/responses";
        List<LongCatClient> invalid = List.of(
                client("fake-secret-before\nfake-secret-after"),
                client("fake-secret\rheader"),
                client("fake-secret\u0000suffix"),
                client("fake-secret\tword"),
                client("fake-secret\u007fword"),
                new LongCatClient(baseUrl + "/fake-url-secret invalid", "test-key", "LongCat-2.5",
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO),
                new LongCatClient("file:///fake-url-secret", "test-key", "LongCat-2.5",
                        Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO));
        for (LongCatClient client : invalid) {
            AiProviderException failure = assertThrows(AiProviderException.class,
                    () -> client.chatCompletion(simpleRequest()));
            assertEquals(503, failure.getHttpStatus());
            assertEquals("LongCat 本机配置格式无效，请检查 API 地址和密钥格式", failure.getMessage());
            assertFalse(failure.toString().contains("fake-secret"));
            assertFalse(failure.toString().contains("fake-url-secret"));
            assertNull(failure.getCause());
        }
        assertEquals(0, requests.get());
    }

    private AiProviderService service(LongCatClient longcat) {
        DeepSeekClient deepseek = new DeepSeekClient("http://127.0.0.1:1/chat", "", Duration.ofSeconds(1), Duration.ofSeconds(1));
        return new AiProviderService(deepseek, mock(DoubaoClient.class), longcat, null,
                "deepseek-model", false, false, 1800, 12000);
    }

    private Map<String, Object> simpleRequest() {
        return Map.of("messages", List.of(Map.of("role", "user", "content", "测试")));
    }

    private LongCatClient client(String key) {
        return new LongCatClient("http://127.0.0.1:" + server.getAddress().getPort() + "/responses", key,
                "LongCat-2.5", Duration.ofSeconds(2), Duration.ofSeconds(2), 2, Duration.ZERO);
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        return JSON.readValue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), MAP_TYPE);
    }

    private String completedText(String text) {
        return JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message",
                "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", text)))),
                "usage", Map.of("input_tokens", 10, "output_tokens", 5, "total_tokens", 15)));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", handler::handle);
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
