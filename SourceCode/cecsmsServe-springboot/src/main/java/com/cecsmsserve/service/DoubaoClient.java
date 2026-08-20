package com.cecsmsserve.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Volcengine Ark/Doubao OpenAI-compatible client. The model value is the Ark
 * endpoint/model ID and is deliberately supplied only through local settings.
 */
@Service
public class DoubaoClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final Duration retryBackoff;

    @Autowired
    public DoubaoClient(
            @Value("${doubao.api.url}") String apiUrl,
            @Value("${doubao.api.key:}") String apiKey,
            @Value("${doubao.api.model:}") String model,
            @Value("${doubao.api.connect-timeout:5s}") Duration connectTimeout,
            @Value("${doubao.api.read-timeout:90s}") Duration readTimeout,
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

    DoubaoClient(
            String apiUrl,
            String apiKey,
            String model,
            Duration connectTimeout,
            Duration readTimeout) {
        this(apiUrl, apiKey, model, connectTimeout, readTimeout, 2, Duration.ofMillis(50));
    }

    public boolean isConfigured() {
        return !apiUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    public String model() {
        return model;
    }

    public Map<String, Object> chatCompletion(Map<String, Object> body) {
        if (!isConfigured()) {
            throw new AiProviderException(
                    "豆包多模态尚未配置，请设置 DOUBAO_API_KEY 与 DOUBAO_MODEL", 503);
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map<String, Object> response = restClient.post()
                        .uri(apiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .body(body)
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (response == null) {
                    throw new AiProviderException("豆包模型返回了空响应", 502);
                }
                return response;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new AiProviderException("豆包模型认证失败，请检查火山方舟配置", 503, ex);
                }
                if ((status == 429 || status >= 500) && attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                if (status == 429) {
                    throw new AiProviderException("豆包模型请求过于频繁，请稍后重试", 503, ex);
                }
                throw new AiProviderException("豆包模型暂时不可用（HTTP " + status + "）", 502, ex);
            } catch (RestClientException ex) {
                if (attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                throw new AiProviderException("无法连接豆包模型，请检查网络后重试", 503, ex);
            }
        }
        throw new AiProviderException("豆包模型暂时不可用", 503);
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(Math.min(2_000, retryBackoff.toMillis() * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("豆包请求已中断", 503, ex);
        }
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
