package com.cecsmsserve.util;

import com.cecsmsserve.controller.ChatController;
import com.cecsmsserve.controller.ReportController;
import com.cecsmsserve.service.AiResourceGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

/** Admit the resolved paid handler after authentication, regardless of URL spelling. */
@Component
public class AiAdmissionInterceptor implements HandlerInterceptor {
    private static final String LEASE = AiAdmissionInterceptor.class.getName() + ".lease";
    private final AiResourceGuard guard;
    private final JsonMapper json;

    public AiAdmissionInterceptor(AiResourceGuard guard, JsonMapper json) {
        this.guard = guard;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!isPaidHandler(handler)) return true;
        Object identity = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        try {
            var lease = guard.acquireRequest(identity instanceof Integer id ? id : null,
                    request.getRemoteAddr());
            request.setAttribute(LEASE, lease);
            return true;
        } catch (AiResourceGuard.Rejected ex) {
            response.setStatus(ex.getHttpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Retry-After", Long.toString(ex.retryAfterSeconds()));
            response.getWriter().write(json.writeValueAsString(Map.of(
                    "code", ex.getHttpStatus(), "success", false,
                    "msg", ex.getMessage(), "error", ex.getMessage())));
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (request.getAttribute(LEASE) instanceof AiResourceGuard.Lease lease) {
            request.removeAttribute(LEASE);
            lease.close();
        }
    }

    static boolean isPaidHandler(Object handler) {
        if (!(handler instanceof HandlerMethod method)) return false;
        return ChatController.class.isAssignableFrom(method.getBeanType())
                && Set.of("chat", "carePlan").contains(method.getMethod().getName())
                || ReportController.class.isAssignableFrom(method.getBeanType())
                && method.getMethod().getName().equals("analyzeReport");
    }
}
