package com.tms.gateway.modules.audit.web;

import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.annotation.Auditable;
import com.tms.gateway.modules.audit.annotation.NoAudit;
import com.tms.gateway.modules.audit.entity.AuditLogEntity;
import com.tms.gateway.modules.audit.service.AuditLogService;
import com.tms.gateway.modules.audit.service.AuditPayloadSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.JsonNode;

/**
 * Records every state-changing request (POST, PUT, PATCH, DELETE) handled by a
 * controller. Fetch requests (GET, HEAD, OPTIONS) and handlers marked
 * {@link NoAudit} are skipped.
 *
 * <p>
 * The entry is assembled on the request thread, after the response has been
 * written, and saved asynchronously by {@link AuditLogService}. Nothing here is
 * allowed to fail the merchant's request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String START_ATTR = AuditLogInterceptor.class.getName() + ".start";

    private final AuditLogService auditLogService;
    private final AuditPayloadSanitizer sanitizer;

    public static boolean isWriteMethod(String method) {
        return method != null && WRITE_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isAudited(request, handler)) {
            request.setAttribute(START_ATTR, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            @Nullable Exception ex) {
        if (request.getAttribute(START_ATTR) == null || !(handler instanceof HandlerMethod method)) {
            return;
        }
        try {
            auditLogService.record(buildEntry(request, response, method, ex));
        } catch (Exception e) {
            log.warn("Failed to build audit log for {} {}: {}", request.getMethod(), request.getRequestURI(),
                    e.getMessage());
        }
    }

    private static boolean isAudited(HttpServletRequest request, Object handler) {
        if (!isWriteMethod(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return false;
        }
        return !method.hasMethodAnnotation(NoAudit.class) && !method.getBeanType().isAnnotationPresent(NoAudit.class);
    }

    AuditLogEntity buildEntry(HttpServletRequest request, HttpServletResponse response, HandlerMethod method,
            @Nullable Exception ex) {
        long start = (Long) request.getAttribute(START_ATTR);
        String endpoint = endpointPattern(request);
        Auditable auditable = method.getMethodAnnotation(Auditable.class);

        JsonNode requestTree = sanitizer.toTree(requestPayload(request));
        JsonNode responseTree = sanitizer.toTree(request.getAttribute(AuditBodyCaptureAdvice.RESPONSE_BODY_ATTR));

        int statusCode = response.getStatus();
        if (ex != null && statusCode < 400) {
            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        AuditLogEntity entry = AuditLogEntity.builder().action(clip(actionName(method, auditable), 150))
                .module(clip(moduleName(auditable, endpoint), 100)).httpMethod(request.getMethod())
                .endpoint(clip(endpoint, 255)).requestPath(clip(request.getRequestURI(), 500))
                .resourceId(clip(resourceId(request), 255))
                .status(statusCode < 400 ? AuditLogEntity.STATUS_SUCCESS : AuditLogEntity.STATUS_FAILED)
                .statusCode(statusCode).resultMessage(textField(responseTree, "message"))
                .errorMessage(ex != null ? ex.getMessage() : textField(responseTree, "error"))
                .requestPayload(sanitizer.toJson(requestTree)).responsePayload(sanitizer.toJson(responseTree))
                .ipAddress(clip(clientIp(request), 64)).userAgent(clip(request.getHeader("User-Agent"), 500))
                .durationMs(System.currentTimeMillis() - start).createdAt(Instant.now()).build();

        GatewayMerchantUserDetails user = currentUser();
        if (user != null) {
            entry.setUserId(user.getUserId());
            entry.setMerchantId(user.getMerchantId());
            entry.setActorEmail(user.getEmail());
            entry.setActorName(
                    AuditLogService.displayName(user.getFirstName(), user.getLastName(), user.getBusinessName()));
        } else {
            // Login, registration and password reset: identify the actor by the email
            // they submitted. The service links it to the account, if one exists.
            entry.setActorEmail(clip(textField(requestTree, "email"), 255));
        }
        return entry;
    }

    /**
     * The request body for JSON endpoints; for multipart endpoints, each part plus
     * metadata (never contents) of the uploaded files.
     */
    @SuppressWarnings("unchecked")
    private Object requestPayload(HttpServletRequest request) {
        Map<String, Object> bodies = (Map<String, Object>) request
                .getAttribute(AuditBodyCaptureAdvice.REQUEST_BODIES_ATTR);
        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);

        if (multipart == null) {
            if (bodies != null && bodies.size() == 1) {
                return bodies.values().iterator().next();
            }
            if (bodies != null && !bodies.isEmpty()) {
                return bodies;
            }
            return request.getParameterMap().isEmpty() ? null : flattenParameters(request.getParameterMap());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (bodies != null) {
            payload.putAll(bodies);
        }
        flattenParameters(multipart.getParameterMap()).forEach(payload::putIfAbsent);
        List<Map<String, Object>> files = new ArrayList<>();
        multipart.getMultiFileMap().forEach((field, list) -> {
            if (payload.containsKey(field)) {
                return; // A data part (e.g. "data" JSON) already captured above
            }
            for (MultipartFile file : list) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("field", field);
                meta.put("filename", file.getOriginalFilename());
                meta.put("content_type", file.getContentType());
                meta.put("size", file.getSize());
                files.add(meta);
            }
        });
        if (!files.isEmpty()) {
            payload.put("files", files);
        }
        return payload;
    }

    private static Map<String, Object> flattenParameters(Map<String, String[]> parameters) {
        Map<String, Object> flat = new LinkedHashMap<>();
        parameters.forEach((key, values) -> flat.put(key, values.length == 1 ? values[0] : List.of(values)));
        return flat;
    }

    private static String actionName(HandlerMethod method, Auditable auditable) {
        if (auditable != null && !auditable.value().isBlank()) {
            return auditable.value();
        }
        Operation operation = method.getMethodAnnotation(Operation.class);
        if (operation != null && !operation.summary().isBlank()) {
            return operation.summary();
        }
        return humanize(method.getMethod().getName());
    }

    /** "saveProfileWithDocs" → "Save profile with docs". */
    static String humanize(String methodName) {
        String spaced = methodName.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String moduleName(Auditable auditable, String endpoint) {
        if (auditable != null && !auditable.module().isBlank()) {
            return auditable.module();
        }
        for (String segment : endpoint.split("/")) {
            if (!segment.isBlank()) {
                return segment.replace('-', '_');
            }
        }
        return "general";
    }

    private static String endpointPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    @SuppressWarnings("unchecked")
    private static String resourceId(HttpServletRequest request) {
        Map<String, String> variables = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        if (variables.size() == 1) {
            return variables.values().iterator().next();
        }
        return variables.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static GatewayMerchantUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof GatewayMerchantUserDetails user ? user : null;
    }

    private static String textField(JsonNode tree, String field) {
        if (tree == null || !tree.isObject()) {
            return null;
        }
        JsonNode value = tree.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
