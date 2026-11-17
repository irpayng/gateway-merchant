package com.tms.gateway.modules.payout.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.grpc.service.ConfigGrpcClient;
import com.tms.gateway.modules.payout.dto.BankResponse;
import com.tms.gateway.modules.payout.dto.NameEnquiryRequest;
import com.tms.gateway.modules.payout.dto.NameEnquiryResponse;
import com.tms.gateway.modules.payout.dto.PayoutDetailDto;
import com.tms.gateway.modules.payout.service.PayoutHistoryService;
import com.tms.gateway.modules.payout.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payout history and bank account verification endpoints for the merchant dashboard.
 */
@RestController
@RequestMapping("/payouts")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = "Payout operations for merchants")
public class PayoutController {

    private final PayoutService payoutService;
    private final PayoutHistoryService payoutHistoryService;
    private final ConfigGrpcClient configGrpcClient;

    @GetMapping
    @Operation(summary = "List payouts", description = "Search and filter the authenticated merchant's payouts")
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Search payment reference, bank, or account") @RequestParam(required = false) String search,
            @Parameter(description = "Payout status: completed, pending, or failed") @RequestParam(required = false) String status,
            @Parameter(description = "Inclusive sales date (yyyy-MM-dd, dd/MM/yyyy, or ISO-8601 date-time)") @RequestParam(name = "start_date", required = false) String startDate,
            @Parameter(description = "Inclusive sales date (yyyy-MM-dd, dd/MM/yyyy, or ISO-8601 date-time)") @RequestParam(name = "end_date", required = false) String endDate,
            @Parameter(description = "Page number (1-indexed)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(payoutHistoryService.list(userDetails.getUserId(), search, status, startDate, endDate,
                page, size));
    }

    @GetMapping("/export")
    @Operation(summary = "Export payouts", description = "Download all payouts matching the supplied filters as CSV")
    public ResponseEntity<String> export(@AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Search payment reference, bank, or account") @RequestParam(required = false) String search,
            @Parameter(description = "Payout status: completed, pending, or failed") @RequestParam(required = false) String status,
            @Parameter(description = "Inclusive sales date (yyyy-MM-dd, dd/MM/yyyy, or ISO-8601 date-time)") @RequestParam(name = "start_date", required = false) String startDate,
            @Parameter(description = "Inclusive sales date (yyyy-MM-dd, dd/MM/yyyy, or ISO-8601 date-time)") @RequestParam(name = "end_date", required = false) String endDate) {
        String csv = payoutHistoryService.exportCsv(userDetails.getUserId(), search, status, startDate, endDate);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payouts.csv\"").body(csv);
    }

    @GetMapping("/{payoutId}")
    @Operation(summary = "Get payout details", description = "Get a payout's details for the authenticated merchant")
    public ResponseEntity<ApiResponse<PayoutDetailDto>> getDetail(
            @AuthenticationPrincipal GatewayMerchantUserDetails userDetails,
            @Parameter(description = "Payout identifier") @PathVariable String payoutId) {
        return ResponseEntity.ok(ApiResponse.success(payoutHistoryService.getDetail(userDetails.getUserId(), payoutId)));
    }

    /**
     * Get list of all supported banks for payouts.
     *
     * <p>
     * Returns all Nigerian banks with their CBN codes. Use the code value in the
     * bank_code field when calling name-enquiry.
     */
    @GetMapping("/banks")
    @Operation(summary = "List all banks", description = "Get all supported banks for payouts")
    public ResponseEntity<ApiResponse<List<BankResponse>>> listBanks() {
        List<BankResponse> banks = configGrpcClient.listBankCodes();
        return ResponseEntity.ok(ApiResponse.success(banks));
    }

    /**
     * Get list of most commonly used banks.
     *
     * <p>
     * Returns a curated list of the top Nigerian banks for quick selection. Useful
     * for showing a "popular banks" section in the UI.
     */
    @GetMapping("/banks/popular")
    @Operation(summary = "List popular banks", description = "Get most commonly used banks for quick selection")
    public ResponseEntity<ApiResponse<List<BankResponse>>> listPopularBanks() {
        List<BankResponse> banks = configGrpcClient.listMostUsedBanks();
        return ResponseEntity.ok(ApiResponse.success(banks));
    }

    /**
     * Verify a bank account holder's name before initiating a payout.
     *
     * <p>
     * Uses NIP (NIBSS Instant Payment) to look up the account holder's name
     * registered with the bank. This should be called before initiating any payout
     * to verify the account belongs to the intended recipient.
     *
     * <p>
     * The bank_code is the CBN institution code (e.g., "000013" for GTBank, "058"
     * for Guaranty Trust Bank).
     */
    @PostMapping("/name-enquiry")
    @Operation(summary = "Verify bank account holder name", description = "Look up the account holder's name before initiating a payout")
    public ResponseEntity<ApiResponse<NameEnquiryResponse>> nameEnquiry(
            @Valid @RequestBody NameEnquiryRequest request) {
        NameEnquiryResponse response = payoutService.nameEnquiry(request.accountNumber(), request.bankCode());
        return ResponseEntity.ok(ApiResponse.success(response, "Account verified"));
    }
}
