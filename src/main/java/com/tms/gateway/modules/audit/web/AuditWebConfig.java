package com.tms.gateway.modules.audit.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the audit interceptor for all application endpoints.
 */
@Configuration
@RequiredArgsConstructor
public class AuditWebConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor).excludePathPatterns("/actuator/**", "/health", "/error",
                "/swagger-ui/**", "/v3/api-docs/**");
    }
}
