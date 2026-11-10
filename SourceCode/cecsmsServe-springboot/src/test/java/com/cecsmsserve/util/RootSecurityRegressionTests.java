package com.cecsmsserve.util;

import com.cecsmsserve.controller.ChatController;
import com.cecsmsserve.controller.ReportController;
import com.cecsmsserve.service.AiResourceGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RootSecurityRegressionTests {
    @Test
    void paidHandlerCoverageIncludesChatCarePlanAndReportButNotStatusOrHistory() {
        ChatController chat = mock(ChatController.class);
        ReportController reports = mock(ReportController.class);
        for (String name : List.of("chat", "carePlan")) {
            assertTrue(AiAdmissionInterceptor.isPaidHandler(handler(chat, ChatController.class, name)));
        }
        assertTrue(AiAdmissionInterceptor.isPaidHandler(handler(reports, ReportController.class, "analyzeReport")));
        for (String name : List.of("status", "providers", "analytics", "actionHistory")) {
            assertFalse(AiAdmissionInterceptor.isPaidHandler(handler(chat, ChatController.class, name)));
        }
        assertFalse(AiAdmissionInterceptor.isPaidHandler(handler(reports, ReportController.class, "selectByuId")));
        assertFalse(AiAdmissionInterceptor.isPaidHandler(new Object()));
    }

    @Test
    void admissionUsesAuthenticatedAttributeAndTransportAddressRatherThanSpoofableHeaders() throws Exception {
        AiResourceGuard guard = mock(AiResourceGuard.class);
        AiResourceGuard.Lease lease = mock(AiResourceGuard.Lease.class);
        when(guard.acquireRequest(19, "192.0.2.7")).thenReturn(lease);
        AiAdmissionInterceptor interceptor = new AiAdmissionInterceptor(guard, JsonMapper.builder().build());
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("X-User-Id", "1");
        Object paid = handler(mock(ChatController.class), ChatController.class, "chat");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), paid));
        verify(guard).acquireRequest(19, "192.0.2.7");
        interceptor.afterCompletion(request, new MockHttpServletResponse(), paid, null);
        verify(lease).close();
    }

    @Test
    void completionReleasesOnceEvenWhenHandlerFailedOrCompletionRepeats() throws Exception {
        AiResourceGuard guard = mock(AiResourceGuard.class);
        AiResourceGuard.Lease lease = mock(AiResourceGuard.Lease.class);
        when(guard.acquireRequest(19, "192.0.2.7")).thenReturn(lease);
        AiAdmissionInterceptor interceptor = new AiAdmissionInterceptor(guard, JsonMapper.builder().build());
        Object paid = handler(mock(ReportController.class), ReportController.class, "analyzeReport");
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, paid));
        interceptor.afterCompletion(request, response, paid, new IllegalStateException("test failure"));
        interceptor.afterCompletion(request, response, paid, null);
        verify(lease, times(1)).close();
    }

    @Test
    void redisOutageRejectsAllThreePaidHandlersAndLeavesStatusAvailable() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        AiResourceGuard guard = new AiResourceGuard(new MockEnvironment()
                .withProperty("agent.redis.enabled", "true"), redis, JsonMapper.builder().build());
        AiAdmissionInterceptor interceptor = new AiAdmissionInterceptor(guard, JsonMapper.builder().build());
        ChatController chat = mock(ChatController.class);
        List<HandlerMethod> paid = List.of(handler(chat, ChatController.class, "chat"),
                handler(chat, ChatController.class, "carePlan"),
                handler(mock(ReportController.class), ReportController.class, "analyzeReport"));
        for (HandlerMethod method : paid) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request(), response, method));
            assertEquals(503, response.getStatus());
            assertEquals("30", response.getHeader("Retry-After"));
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertTrue(response.getContentAsString().contains("\"success\":false"));
        }
        assertTrue(interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(chat, ChatController.class, "status")));
    }

    @Test
    void quotaDenialIs429AndNoLeaseIsLeftOnTheRequest() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        AiResourceGuard guard = new AiResourceGuard(new MockEnvironment()
                .withProperty("agent.redis.enabled", "false")
                .withProperty("agent.ai.protection.user-per-minute", "1"), redis, JsonMapper.builder().build());
        AiAdmissionInterceptor interceptor = new AiAdmissionInterceptor(guard, JsonMapper.builder().build());
        Object paid = handler(mock(ChatController.class), ChatController.class, "chat");
        MockHttpServletRequest first = request();
        assertTrue(interceptor.preHandle(first, new MockHttpServletResponse(), paid));
        interceptor.afterCompletion(first, new MockHttpServletResponse(), paid, null);
        MockHttpServletRequest denied = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(denied, response, paid));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertDoesNotThrow(() -> interceptor.afterCompletion(denied, response, paid, null));
    }

    @Test
    void authenticationIsRegisteredBeforeAdmissionAndHealthPathsRemainExcludedFromJwt() {
        JWTInterceptor jwt = mock(JWTInterceptor.class);
        AiAdmissionInterceptor admission = mock(AiAdmissionInterceptor.class);
        ExposedRegistry registry = new ExposedRegistry();
        new JWTInterceptorConfig(jwt, admission).addInterceptors(registry);
        List<Object> registered = registry.values();
        assertEquals(2, registered.size());
        MappedInterceptor authentication = (MappedInterceptor) registered.getFirst();
        assertSame(jwt, authentication.getInterceptor());
        assertTrue(Arrays.asList(authentication.getExcludePathPatterns()).contains("/actuator/health/**"));
        assertSame(admission, ((MappedInterceptor) registered.get(1)).getInterceptor());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.7");
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, 19);
        return request;
    }

    private static HandlerMethod handler(Object bean, Class<?> type, String name) {
        return new HandlerMethod(bean, Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow());
    }

    private static final class ExposedRegistry extends InterceptorRegistry {
        List<Object> values() { return getInterceptors(); }
    }
}
