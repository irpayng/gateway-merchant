package com.tms.gateway.modules.transaction.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.transaction.dto.TransactionDetailDto;
import com.tms.gateway.modules.transaction.dto.TransactionSummaryDto;
import com.tms.gateway.modules.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transaction endpoints for merchant dashboard. All endpoints are scoped to the
 * authenticated user's transactions.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction operations for merchant dashboard")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Get transaction summary statistics for dashboard cards.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get transaction summary", description = "Get aggregated transaction statistics for dashboard cards")
    public ResponseEntity<ApiResponse<TransactionSummaryDto>> getSummary(
            @AuthenticationPrincipal GatewayMerchantUserDetails userDetails) {
        TransactionSummaryDto summary = transactionService.getSummary(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * Get paginated list of transactions with optional filters.
     */
    @GetMapping
    @Operation(summary = "List transactions", description = "Get paginated list of transactions with optional filters")
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Transaction status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Payment method filter") @RequestParam(name = "payment_method", required = false) String paymentMethod,
            @Parameter(description = "Start date filter (ISO-8601)") @RequestParam(name = "start_date", required = false) Instant startDate,
            @Parameter(description = "End date filter (ISO-8601)") @RequestParam(name = "end_date", required = false) Instant endDate,
            @Parameter(description = "Search by reference, email, or name") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = transactionService.list(userDetails.getUserId(), status, paymentMethod,
                startDate, endDate, search, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get single transaction details.
     */
    @GetMapping("/{reference}")
    @Operation(summary = "Get transaction detail", description = "Get full details of a single transaction")
    public ResponseEntity<ApiResponse<TransactionDetailDto>> getDetail(
            @AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Transaction reference") @PathVariable String reference) {
        TransactionDetailDto detail = transactionService.getDetail(userDetails.getUserId(), reference);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }
}
