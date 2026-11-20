package com.tms.gateway.modules.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Audit log entry as returned to the merchant dashboard.
 */
@Schema(name = "AuditLog", description = "One action performed on the merchant account")
public record AuditLogDto(@Schema(description = "Audit log ID", example = "1024") @JsonProperty("id") Long id,
        @Schema(description = "Human-readable action", example = "Rotate API key") @JsonProperty("action") String action,
        @Schema(description = "Area of the dashboard the action belongs to", example = "api_keys") @JsonProperty("module") String module,
        @Schema(description = "Who performed the action") @JsonProperty("performed_by") Actor performedBy,
        @Schema(description = "When the action completed (UTC, ISO-8601)", example = "2026-11-20T09:15:30Z") @JsonProperty("performed_at") Instant performedAt,
        @Schema(description = "Date of the action in the business timezone (yyyy-MM-dd)", example = "2026-11-20") @JsonProperty("date") String date,
        @Schema(description = "Time of the action in the business timezone (HH:mm:ss)", example = "10:15:30") @JsonProperty("time") String time,
        @Schema(description = "Outcome: success for HTTP status below 400, otherwise failed", allowableValues = {
                "success", "failed"}, example = "success") @JsonProperty("status") String status,
        @Schema(description = "HTTP status code returned to the caller", example = "200") @JsonProperty("status_code") Integer statusCode,
        @Schema(description = "The `message` field of the response, if any", example = "API key rotated") @JsonProperty("result_message") String resultMessage,
        @Schema(description = "Error detail when the action failed with an unhandled error, or the response's "
                + "`error` field", nullable = true) @JsonProperty("error_message") String errorMessage,
        @Schema(description = "HTTP method", example = "POST") @JsonProperty("http_method") String httpMethod,
        @Schema(description = "Endpoint pattern that handled the request", example = "/api-keys/{id}/rotate") @JsonProperty("endpoint") String endpoint,
        @Schema(description = "Actual request path", example = "/api-keys/key_8f2a/rotate") @JsonProperty("request_path") String requestPath,
        @Schema(description = "ID from the path (e.g. the API key, dispute or webhook acted on)", example = "key_8f2a", nullable = true) @JsonProperty("resource_id") String resourceId,
        @Schema(description = "Request body with sensitive fields replaced by [REDACTED]. Uploads list only "
                + "file name, type and size. Payloads over 32 KB are replaced by a truncated preview.", nullable = true) @JsonProperty("request_payload") Object requestPayload,
        @Schema(description = "Response body with sensitive fields replaced by [REDACTED]", nullable = true) @JsonProperty("response_payload") Object responsePayload,
        @Schema(description = "Client IP (first X-Forwarded-For entry when behind a proxy)", example = "102.89.4.17") @JsonProperty("ip_address") String ipAddress,
        @Schema(description = "Client User-Agent header", example = "Mozilla/5.0") @JsonProperty("user_agent") String userAgent,
        @Schema(description = "Time taken to handle the request, in milliseconds", example = "184") @JsonProperty("duration_ms") Long durationMs){

    /** The user who performed the action. */
    @Schema(name = "AuditLogActor", description = "Who performed the action. For login, registration and "
            + "password reset, this is the account matching the submitted email; user_id is null when no account "
            + "matches.")
    public record Actor(
            @Schema(description = "User ID", example = "4", nullable = true) @JsonProperty("user_id") Long userId,
            @Schema(description = "Merchant ID", example = "MID000123", nullable = true) @JsonProperty("merchant_id") String merchantId,
            @Schema(description = "Email address", example = "owner@acme.ng") @JsonProperty("email") String email,
            @Schema(description = "Full name, or the business name when no name is set", example = "Ada Obi") @JsonProperty("name") String name) {
    }
}
