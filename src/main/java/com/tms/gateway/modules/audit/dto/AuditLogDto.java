package com.tms.gateway.modules.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Audit log entry as returned to the merchant dashboard.
 */
public record AuditLogDto(@JsonProperty("id") Long id, @JsonProperty("action") String action,
        @JsonProperty("module") String module, @JsonProperty("performed_by") Actor performedBy,
        @JsonProperty("performed_at") Instant performedAt, @JsonProperty("date") String date,
        @JsonProperty("time") String time, @JsonProperty("status") String status,
        @JsonProperty("status_code") Integer statusCode, @JsonProperty("result_message") String resultMessage,
        @JsonProperty("error_message") String errorMessage, @JsonProperty("http_method") String httpMethod,
        @JsonProperty("endpoint") String endpoint, @JsonProperty("request_path") String requestPath,
        @JsonProperty("resource_id") String resourceId, @JsonProperty("request_payload") Object requestPayload,
        @JsonProperty("response_payload") Object responsePayload, @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("user_agent") String userAgent, @JsonProperty("duration_ms") Long durationMs) {

    /** The user who performed the action. */
    public record Actor(@JsonProperty("user_id") Long userId, @JsonProperty("merchant_id") String merchantId,
            @JsonProperty("email") String email, @JsonProperty("name") String name) {
    }
}
