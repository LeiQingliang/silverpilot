package com.cecsmsserve.service;

import com.cecsmsserve.service.DeepSeekClient.DeepSeekException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerTokenAndParsesAssistantText() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, """
                    {"choices":[{"message":{"role":"assistant","content":"  测试回复  "}}]}
                    """);
        });

        DeepSeekClient client = client("test-api-key");
        Map<String, Object> response = client.chatCompletion(Map.of(
                "model", "deepseek-v4-flash",
                "messages", java.util.List.of(Map.of("role", "user", "content", "你好"))));

        assertEquals("Bearer test-api-key", authorization.get());
        assertEquals("测试回复", client.firstText(response));
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkRequest() {
        DeepSeekClient client = new DeepSeekClient(
                "http://127.0.0.1:1/chat", "   ", Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertFalse(client.isConfigured());
        DeepSeekException exception = assertThrows(
                DeepSeekException.class,
                () -> client.chatCompletion(Map.of("messages", java.util.List.of())));

        assertEquals(503, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("DEEPSEEK_API_KEY"));
    }

    @Test
    void mapsAuthenticationFailureToSafeServiceError() throws IOException {
        startServer(exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 401, "{\"error\":{\"message\":\"upstream-secret-detail\"}}");
        });

        DeepSeekException exception = assertThrows(
                DeepSeekException.class,
                () -> client("invalid-key").chatCompletion(Map.of("messages", java.util.List.of())));

        assertEquals(503, exception.getHttpStatus());
        assertEquals("AI 服务认证失败，请更新 DEEPSEEK_API_KEY", exception.getMessage());
        assertFalse(exception.getMessage().contains("upstream-secret-detail"));
    }

    private DeepSeekClient client(String key) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        return new DeepSeekClient(url, key, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> handler.handle(exchange));
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
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
