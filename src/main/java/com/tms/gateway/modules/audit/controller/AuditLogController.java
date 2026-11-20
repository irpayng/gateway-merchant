package com.tms.gateway.modules.audit.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.dto.AuditLogDto;
import com.tms.gateway.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Read access to the merchant's audit trail.
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Record of actions performed on the merchant account. Every POST, PUT, "
        + "PATCH and DELETE request is recorded with who performed it, when, and the result. GET requests are not "
        + "recorded. Passwords, OTPs, tokens, API key secrets, webhook secrets and PINs are replaced with "
        + "[REDACTED]; for file uploads only the file name, type and size are kept.")
public class AuditLogController {

    private static final String LIST_EXAMPLE = """
            {
              "data": [
                {
                  "id": 1024,
                  "action": "Rotate API key",
                  "module": "api_keys",
                  "performed_by": {
                    "user_id": 4,
                    "merchant_id": "MID000123",
                    "email": "owner@acme.ng",
                    "name": "Ada Obi"
                  },
                  "performed_at": "2026-11-20T09:15:30Z",
                  "date": "2026-11-20",
                  "time": "10:15:30",
                  "status": "success",
                  "status_code": 200,
                  "result_message": "API key rotated",
                  "error_message": null,
                  "http_method": "POST",
                  "endpoint": "/api-keys/{id}/rotate",
                  "request_path": "/api-keys/key_8f2a/rotate",
                  "resource_id": "key_8f2a",
                  "request_payload": null,
                  "response_payload": {
                    "code": 200,
                    "message": "API key rotated",
                    "data": { "id": "key_8f2a", "public_id": "pk_live_x1", "secret": "[REDACTED]" }
                  },
                  "ip_address": "102.89.4.17",
                  "user_agent": "Mozilla/5.0",
                  "duration_ms": 184
                }
              ],
              "links": {
                "first": "/audit-logs?page=1",
                "last": "/audit-logs?page=1",
                "prev": null,
                "next": null
              },
              "meta": {
                "current_page": 1,
                "from": 1,
                "last_page": 1,
                "path": "/audit-logs",
                "per_page": 15,
                "to": 1,
                "total": 1
              },
              "filters": {
                "search": null,
                "module": "api_keys",
                "status": null,
                "start_date": null,
                "end_date": null
              }
            }
            """;

    private static final String FAILED_EXAMPLE = """
            {
              "code": 200,
              "message": "Successful",
              "data": {
                "id": 1025,
                "action": "Close dispute",
                "module": "disputes",
                "performed_by": { "user_id": 4, "merchant_id": "MID000123", "email": "owner@acme.ng", "name": "Ada Obi" },
                "performed_at": "2026-11-20T09:20:02Z",
                "date": "2026-11-20",
                "time": "10:20:02",
                "status": "failed",
                "status_code": 422,
                "result_message": "Dispute is already closed",
                "error_message": null,
                "http_method": "PATCH",
                "endpoint": "/disputes/{id}/close",
                "request_path": "/disputes/77/close",
                "resource_id": "77",
                "request_payload": null,
                "response_payload": { "code": 422, "message": "Dispute is already closed" },
                "ip_address": "102.89.4.17",
                "user_agent": "Mozilla/5.0",
                "duration_ms": 96
              }
            }
            """;

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "List audit logs", description = "Paginated list of actions performed on the merchant "
            + "account, newest first, including actions by every user of the merchant. Login, registration and "
            + "password-reset attempts are listed under module `auth`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of audit log entries", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "page", value = LIST_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content)})
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Case-insensitive match on action, request path, actor email or resource ID", example = "api key") @RequestParam(required = false) String search,
            @Parameter(description = "Module: the first segment of the endpoint path, with hyphens replaced by "
                    + "underscores", schema = @Schema(allowableValues = {"auth", "api_keys", "webhooks", "compliance",
                            "disputes", "customers"})) @RequestParam(required = false) String module,
            @Parameter(description = "Outcome of the action", schema = @Schema(allowableValues = {"success",
                    "failed"})) @RequestParam(required = false) String status,
            @Parameter(description = "Only entries at or after this instant (ISO-8601)", example = "2026-11-01T00:00:00Z") @RequestParam(name = "start_date", required = false) Instant startDate,
            @Parameter(description = "Only entries at or before this instant (ISO-8601)", example = "2026-11-30T23:59:59Z") @RequestParam(name = "end_date", required = false) Instant endDate,
            @Parameter(description = "Page number (1-indexed)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size, 1 to 100", example = "15") @RequestParam(name = "limit", defaultValue = "15") int size) {
        return ResponseEntity.ok(auditLogService.list(user, search, module, status, startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log", description = "Full detail of one audit log entry, including the redacted "
            + "request and response bodies.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The audit log entry", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "failed action", value = FAILED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No entry with this ID belongs to the caller's merchant", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"code\":404,\"message\":\"Audit log not found.\"}")))})
    public ResponseEntity<ApiResponse<AuditLogDto>> show(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Audit log ID", example = "1025") @PathVariable Long id) {
        return auditLogService.show(user, id).map(dto -> ResponseEntity.ok(ApiResponse.success(dto))).orElseGet(
                () -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "Audit log not found.")));
    }
}
