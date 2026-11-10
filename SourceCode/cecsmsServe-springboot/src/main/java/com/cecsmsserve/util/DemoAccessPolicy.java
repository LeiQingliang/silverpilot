package com.cecsmsserve.util;

import com.cecsmsserve.controller.ChatController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Limits the shared demo identities without changing ordinary users' roles. */
@Component
public class DemoAccessPolicy {
    private final boolean enabled;
    private final Set<Integer> userIds;

    public DemoAccessPolicy(
            @Value("${app.security.demo-read-only.enabled:false}") boolean enabled,
            @Value("${app.security.demo-read-only.user-ids:1,17}") String userIds) {
        this.enabled = enabled;
        this.userIds = Arrays.stream(userIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
        if (this.userIds.stream().anyMatch(id -> id <= 0) || enabled && this.userIds.isEmpty()) {
            throw new IllegalArgumentException("Demo read-only user IDs must be positive and non-empty when enabled");
        }
    }

    public boolean isReadOnly(Integer userId) {
        return enabled && userId != null && userIds.contains(userId);
    }

    public boolean permits(String httpMethod, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return false;
        // Check the resolved Java handler, not a URI prefix which matrix parameters
        // or encoded paths can spell differently. Audited GET handlers only read
        // business data; shared accounts use the query-only action history path.
        if ("GET".equals(httpMethod) || "HEAD".equals(httpMethod)) {
            return method.hasMethodAnnotation(GetMapping.class);
        }
        // Chat may calculate/read and prepare a confirmation; execution endpoints
        // are deliberately excluded. No other POST/PUT/PATCH/DELETE is allowed.
        return "POST".equals(httpMethod)
                && method.getBeanType().equals(ChatController.class)
                && Set.of("chat", "carePlan").contains(method.getMethod().getName());
    }
}
