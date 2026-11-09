package com.tms.gateway.modules.refund.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.refund.dto.RefundDetailDto;
import com.tms.gateway.modules.refund.dto.RefundPageResponse;
import com.tms.gateway.modules.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refunds")
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Read-only refund operations for merchant dashboard")
public class RefundController {

    private final RefundService refundService;

    @GetMapping
    @Operation(summary = "List refunds", description = "Search and filter the authenticated merchant's refunds")
    public ResponseEntity<ApiResponse<RefundPageResponse>> list(
            @AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Search refund amount, customer name, or customer email")
            @RequestParam(required = false) String search,
            @Parameter(description = "The only supported refund status is processed")
            @RequestParam(required = false) String status,
            @Parameter(description = "Inclusive refund creation-time lower bound (ISO-8601)")
            @RequestParam(name = "start_date", required = false) Instant startDate,
            @Parameter(description = "Exclusive refund creation-time upper bound (ISO-8601)")
            @RequestParam(name = "end_date", required = false) Instant endDate,
            @Parameter(description = "One-based page number")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        RefundPageResponse response = refundService.list(userDetails.getUserId(), search, status, startDate, endDate,
                page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{refundId}")
    @Operation(summary = "Get refund details", description = "Get a refund's details for the authenticated merchant")
    public ResponseEntity<ApiResponse<RefundDetailDto>> getDetail(
            @AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Refund identifier") @PathVariable String refundId) {
        return ResponseEntity.ok(ApiResponse.success(refundService.getDetail(userDetails.getUserId(), refundId)));
    }
}
