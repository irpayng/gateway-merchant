package com.tms.gateway.modules.dispute.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.annotation.Auditable;
import com.tms.gateway.modules.audit.annotation.NoAudit;
import com.tms.gateway.modules.dispute.dto.*;
import com.tms.gateway.modules.dispute.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Dispute endpoints for merchant dashboard.
 */
@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Dispute operations for merchant dashboard")
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * Get paginated list of disputes with filters.
     */
    @GetMapping
    @Operation(summary = "List disputes", description = "Get paginated list of disputes with optional filters")
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Search by ID, subject, or reference") @RequestParam(required = false) String search,
            @Parameter(description = "Start date filter (ISO-8601)") @RequestParam(name = "start_date", required = false) Instant startDate,
            @Parameter(description = "End date filter (ISO-8601)") @RequestParam(name = "end_date", required = false) Instant endDate,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(name = "limit", defaultValue = "15") int size) {
        Map<String, Object> response = disputeService.list(user.getUserId(), status, search, startDate, endDate, page,
                size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get dispute threads for inbox/messenger UI.
     */
    @GetMapping("/threads")
    @Operation(summary = "List dispute threads", description = "Get dispute threads for messenger-style inbox")
    public ResponseEntity<ApiResponse<List<DisputeThreadDto>>> threads(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Filter: all, open, closed, unread") @RequestParam(required = false) String filter,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Max threads to return") @RequestParam(defaultValue = "60") int limit,
            @Parameter(description = "Offset for pagination") @RequestParam(defaultValue = "0") int offset) {
        List<DisputeThreadDto> threads = disputeService.threads(user.getUserId(), filter, search, limit, offset);
        return ResponseEntity.ok(ApiResponse.success(threads));
    }

    /**
     * Get total unread message count.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Get total unread messages across all disputes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unreadCount(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {
        long count = disputeService.totalUnread(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("unread_count", count)));
    }

    /**
     * Get single dispute detail.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get dispute detail", description = "Get full details of a single dispute including conversations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> show(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Dispute ID") @PathVariable Long id) {
        Map<String, Object> dispute = disputeService.show(user.getUserId(), id);
        if (dispute == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "Dispute not found."));
        }
        // Mark as read when viewing
        disputeService.markRead(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(dispute));
    }

    /**
     * Mark a dispute as read.
     */
    @NoAudit
    @PatchMapping("/{id}/mark-read")
    @Operation(summary = "Mark dispute as read", description = "Mark a dispute thread as read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markRead(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Dispute ID") @PathVariable Long id) {
        disputeService.markRead(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse
                .success(Map.of("dispute_id", id, "unread_count", disputeService.totalUnread(user.getUserId()))));
    }

    /**
     * Create a new dispute.
     */
    @PostMapping
    @Operation(summary = "Create dispute", description = "Create a new dispute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Valid @RequestBody CreateDisputeRequest request) {
        Map<String, Object> result = disputeService.create(user, request);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (!success) {
            String message = result.getOrDefault("message", "Failed to create dispute").toString();
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(422, message));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Map<String, Object>>builder().code(201)
                        .message(result.getOrDefault("message", "Dispute created successfully").toString()).data(result)
                        .build());
    }

    /**
     * Add a message to a dispute.
     */
    @Auditable("Reply to dispute")
    @PostMapping("/{id}/add-conversation")
    @Operation(summary = "Add conversation", description = "Add a message to an existing dispute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addConversation(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Dispute ID") @PathVariable Long id,
            @Valid @RequestBody AddConversationRequest request) {
        Map<String, Object> result = disputeService.addConversation(user, id, request.getMessage());
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (!success) {
            String message = result.getOrDefault("message", "Failed to add conversation").toString();
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(422, message));
        }
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().code(200).message("Conversation added")
                .data(result).build());
    }

    /**
     * Close a dispute.
     */
    @PatchMapping("/{id}/close")
    @Operation(summary = "Close dispute", description = "Close an existing dispute")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(
            @AuthenticationPrincipal GatewayMerchantUserDetails user,
            @Parameter(description = "Dispute ID") @PathVariable Long id) {
        Map<String, Object> result = disputeService.close(user, id);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (!success) {
            String message = result.getOrDefault("message", "Failed to close dispute").toString();
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(422, message));
        }
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder().code(200).message("Dispute closed").data(result).build());
    }
}
