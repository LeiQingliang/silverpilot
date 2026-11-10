package com.cecsmsserve.util;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class JWTInterceptorConfig implements WebMvcConfigurer {

    private final JWTInterceptor jwtInterceptor;
    private final AiAdmissionInterceptor aiAdmissionInterceptor;

    public JWTInterceptorConfig(JWTInterceptor jwtInterceptor, AiAdmissionInterceptor aiAdmissionInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.aiAdmissionInterceptor = aiAdmissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/error",
                        "/favicon.ico",
                        "/assets/**",
                        "/css/**",
                        "/js/**",
                        "/image/**",
                        "/user/register",
                        "/user/login/**",
                        "/user/getVerificationCode/**",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/mcp",
                        "/mcp/**"
                );
        registry.addInterceptor(aiAdmissionInterceptor).addPathPatterns("/**");
    }
}
