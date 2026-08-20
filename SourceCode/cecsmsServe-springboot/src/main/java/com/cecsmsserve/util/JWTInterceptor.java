package com.cecsmsserve.util;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JWTInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = JWTInterceptor.class.getName() + ".userId";
    public static final String USER_ROLE_ATTRIBUTE = JWTInterceptor.class.getName() + ".userRole";

    private static final Logger log = LoggerFactory.getLogger(JWTInterceptor.class);

    private final IUserService userService;
    private final JWTUtil jwtUtil;

    public JWTInterceptor(IUserService userService, JWTUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            sendUnauthorized(response, "请先登录");
            return false;
        }

        try {
            Integer userId = jwtUtil.getUserId(token);
            User user = userService.getById(userId);
            if (user == null) {
                sendUnauthorized(response, "用户不存在，请重新登录");
                return false;
            }
            request.setAttribute(USER_ID_ATTRIBUTE, userId);
            request.setAttribute(USER_ROLE_ATTRIBUTE, user.getRoleId());
            return true;
        } catch (JWTVerificationException | IllegalArgumentException ex) {
            String safeRequestUri = request.getRequestURI().replace('\r', '_').replace('\n', '_');
            log.debug("JWT validation failed for {} {} ({})",
                    request.getMethod(), safeRequestUri, ex.getClass().getSimpleName());
            sendUnauthorized(response, "登录已过期，请重新登录");
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        String legacyToken = request.getHeader("token");
        return legacyToken == null ? null : legacyToken.trim();
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"msg\":\"" + message + "\",\"result\":null}");
    }
}
