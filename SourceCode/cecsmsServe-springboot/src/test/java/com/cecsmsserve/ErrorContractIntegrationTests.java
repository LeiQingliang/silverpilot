package com.cecsmsserve;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorContractIntegrationTests {

    @LocalServerPort
    private int port;

    @Test
    void missingLoginParametersReturnStructuredBadRequestInsteadOfServerError() throws Exception {
        HttpResponse<String> response = send("POST", "/user/login/contract-test");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":400"));
        assertTrue(response.body().contains("缺少必要参数"));
    }

    @Test
    void unsupportedPublicEndpointMethodReturnsStructuredMethodNotAllowed() throws Exception {
        HttpResponse<String> response = send("PUT", "/user/getVerificationCode/contract-test");

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("\"code\":405"));
        assertTrue(response.body().contains("请求方法不支持"));
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
