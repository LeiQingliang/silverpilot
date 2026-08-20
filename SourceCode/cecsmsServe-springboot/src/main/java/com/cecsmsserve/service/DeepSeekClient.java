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
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;
    private final int maxAttempts;
    private final Duration retryBackoff;

    @Autowired
    public DeepSeekClient(
            @Value("${deepseek.api.url}") String apiUrl,
            @Value("${deepseek.api.key:}") String apiKey,
            @Value("${deepseek.api.connect-timeout:5s}") Duration connectTimeout,
            @Value("${deepseek.api.read-timeout:60s}") Duration readTimeout,
            @Value("${agent.ai.retry.max-attempts:2}") int maxAttempts,
            @Value("${agent.ai.retry.backoff:350ms}") Duration retryBackoff) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 4));
        this.retryBackoff = retryBackoff.isNegative() ? Duration.ZERO : retryBackoff;
    }

    DeepSeekClient(
            String apiUrl,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout) {
        this(apiUrl, apiKey, connectTimeout, readTimeout, 2, Duration.ofMillis(50));
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public Map<String, Object> chatCompletion(Map<String, Object> body) {
        if (!isConfigured()) {
            throw new DeepSeekException("AI 服务尚未配置，请在本机设置 DEEPSEEK_API_KEY", 503);
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
                    throw new DeepSeekException("AI 服务返回了空响应", 502);
                }
                return response;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new DeepSeekException("AI 服务认证失败，请更新 DEEPSEEK_API_KEY", 503, ex);
                }
                if ((status == 429 || status >= 500) && attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                if (status == 429) {
                    throw new DeepSeekException("AI 服务请求过于频繁，请稍后重试", 503, ex);
                }
                throw new DeepSeekException("AI 服务暂时不可用（HTTP " + status + "）", 502, ex);
            } catch (RestClientException ex) {
                if (attempt < maxAttempts) {
                    pause(attempt);
                    continue;
                }
                throw new DeepSeekException("无法连接 AI 服务，请检查网络后重试", 503, ex);
            }
        }
        throw new DeepSeekException("AI 服务暂时不可用", 503);
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(Math.min(2_000, retryBackoff.toMillis() * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DeepSeekException("AI 请求已中断", 503, ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> firstMessage(Map<String, Object> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()
                || !(choices.getFirst() instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new DeepSeekException("AI 服务返回格式异常", 502);
        }
        return (Map<String, Object>) message;
    }

    public String firstText(Map<String, Object> response) {
        Object content = firstMessage(response).get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            throw new DeepSeekException("AI 服务未返回有效文本", 502);
        }
        return text.trim();
    }

    public static final class DeepSeekException extends AiProviderException {

        public DeepSeekException(String message, int httpStatus) {
            super(message, httpStatus);
        }

        public DeepSeekException(String message, int httpStatus, Throwable cause) {
            super(message, httpStatus, cause);
        }
    }
}
