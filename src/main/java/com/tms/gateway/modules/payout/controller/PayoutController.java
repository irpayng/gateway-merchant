package com.tms.gateway.modules.payout.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.modules.payout.dto.BankResponse;
import com.tms.gateway.modules.payout.dto.NameEnquiryRequest;
import com.tms.gateway.modules.payout.dto.NameEnquiryResponse;
import com.tms.gateway.modules.payout.service.ConfigService;
import com.tms.gateway.modules.payout.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payout endpoints for merchant dashboard. Allows merchants to verify bank
 * accounts before initiating payouts to customers.
 */
@RestController
@RequestMapping("/payouts")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = "Payout operations for merchants")
public class PayoutController {

    private final PayoutService payoutService;
    private final ConfigService configService;

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
        List<BankResponse> banks = configService.getBanks();
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
        List<BankResponse> banks = configService.getMostUsedBanks();
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
