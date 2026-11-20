package com.tms.gateway.modules.audit.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.dto.AuditLogDto;
import com.tms.gateway.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Audit Logs", description = "Record of actions performed on the merchant account")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "List audit logs", description = "Paginated list of actions performed on the merchant "
            + "account, newest first. Fetch (read-only) requests are not recorded.")
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Search action, path, actor email or resource ID") @RequestParam(required = false) String search,
            @Parameter(description = "Module, e.g. auth, api_keys, disputes") @RequestParam(required = false) String module,
            @Parameter(description = "success or failed") @RequestParam(required = false) String status,
            @Parameter(description = "Start date filter (ISO-8601)") @RequestParam(name = "start_date", required = false) Instant startDate,
            @Parameter(description = "End date filter (ISO-8601)") @RequestParam(name = "end_date", required = false) Instant endDate,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(name = "limit", defaultValue = "15") int size) {
        return ResponseEntity.ok(auditLogService.list(user, search, module, status, startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log", description = "Full detail of one audit log entry")
    public ResponseEntity<ApiResponse<AuditLogDto>> show(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Audit log ID") @PathVariable Long id) {
        return auditLogService.show(user, id).map(dto -> ResponseEntity.ok(ApiResponse.success(dto))).orElseGet(
                () -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "Audit log not found.")));
    }
}
