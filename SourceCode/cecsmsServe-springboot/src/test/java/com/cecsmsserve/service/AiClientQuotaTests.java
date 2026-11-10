package com.cecsmsserve.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiClientQuotaTests {
    @Test
    void everyDeepSeekRetryRequiresAnotherBudgetReservation() throws Exception { verify("deepseek"); }
    @Test
    void everyDoubaoRetryRequiresAnotherBudgetReservation() throws Exception { verify("doubao"); }
    @Test
    void everyLongCatRetryRequiresAnotherBudgetReservation() throws Exception { verify("longcat"); }

    private void verify(String provider) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AiResourceGuard guard = new AiResourceGuard(new AiResourceGuard.Limits(
                    2, 1, 2, 20, 3, 6, 200, 20, 40, 1, 500_000, 65536,
                    Duration.ofMinutes(5), Duration.ofSeconds(90)), Clock.systemUTC(),
                    new LocalAiQuotaStore(4096), JsonMapper.builder().build());
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";
            Map<String, Object> body = Map.of("model", "test", "max_tokens", 20,
                    "messages", List.of(Map.of("role", "user", "content", "hello")));
            try (var ignored = guard.acquireRequest(17, "127.0.0.1")) {
                var denied = assertThrows(AiResourceGuard.Rejected.class, () -> {
                    switch (provider) {
                        case "deepseek" -> {
                            var client = new DeepSeekClient(endpoint, "loopback-test", Duration.ofSeconds(1), Duration.ofSeconds(1));
                            client.setResourceGuard(guard);
                            client.chatCompletion(body);
                        }
                        case "doubao" -> {
                            var client = new DoubaoClient(endpoint, "loopback-test", "test", Duration.ofSeconds(1), Duration.ofSeconds(1));
                            client.setResourceGuard(guard);
                            client.chatCompletion(body);
                        }
                        case "longcat" -> {
                            var client = new LongCatClient(endpoint, "loopback-test", "test", Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ZERO);
                            client.setResourceGuard(guard);
                            client.chatCompletion(body);
                        }
                        default -> throw new AssertionError("Unsupported test provider");
                    }
                });
                assertEquals(429, denied.getHttpStatus());
                assertEquals(1, requests.get(), "Second HTTP attempt must not be sent after budget exhaustion");
            }
        } finally {
            server.stop(0);
        }
    }
}
