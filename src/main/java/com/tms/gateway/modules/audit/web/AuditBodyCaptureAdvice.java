package com.tms.gateway.modules.audit.web;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Keeps the deserialised request body and the response body of write requests
 * on the request, so {@link AuditLogInterceptor} can record them without
 * re-reading the input stream.
 */
@ControllerAdvice
public class AuditBodyCaptureAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {

    static final String REQUEST_BODIES_ATTR = AuditBodyCaptureAdvice.class.getName() + ".requestBodies";
    static final String RESPONSE_BODY_ATTR = AuditBodyCaptureAdvice.class.getName() + ".responseBody";

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet
                && AuditLogInterceptor.isWriteMethod(servlet.getRequest().getMethod())) {
            HttpServletRequest request = servlet.getRequest();
            Map<String, Object> bodies = (Map<String, Object>) request.getAttribute(REQUEST_BODIES_ATTR);
            if (bodies == null) {
                bodies = new LinkedHashMap<>();
                request.setAttribute(REQUEST_BODIES_ATTR, bodies);
            }
            String name = parameter.getParameterName();
            bodies.put(name == null ? "body" : name, body);
        }
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servlet
                && AuditLogInterceptor.isWriteMethod(servlet.getServletRequest().getMethod())) {
            servlet.getServletRequest().setAttribute(RESPONSE_BODY_ATTR, body);
        }
        return body;
    }
}
