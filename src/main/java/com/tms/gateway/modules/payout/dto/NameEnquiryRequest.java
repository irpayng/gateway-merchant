package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for bank account name enquiry.
 */
public record NameEnquiryRequest(
        @NotBlank(message = "Account number is required") @Pattern(regexp = "\\d{10}", message = "Account number must be exactly 10 digits") @JsonProperty("account_number") String accountNumber,

        @NotBlank(message = "Bank code is required") @JsonProperty("bank_code") String bankCode) {
}
