package com.tms.gateway.modules.payout.controller;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.modules.payout.dto.NameEnquiryRequest;
import com.tms.gateway.modules.payout.dto.NameEnquiryResponse;
import com.tms.gateway.modules.payout.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
