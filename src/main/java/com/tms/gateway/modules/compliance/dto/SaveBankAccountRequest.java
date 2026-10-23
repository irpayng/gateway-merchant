package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to save a single bank account (step 4).
 */
public record SaveBankAccountRequest(Long id,

        @JsonProperty("bank_code") @NotBlank(message = "Bank code is required") String bankCode,

        @JsonProperty("bank_name") String bankName,

        @JsonProperty("account_number") @NotBlank(message = "Account number is required") @Pattern(regexp = "\\d{10}", message = "Account number must be 10 digits") String accountNumber,

        @JsonProperty("account_name") @NotBlank(message = "Account name is required") String accountName,

        @JsonProperty("is_primary") boolean isPrimary,

        String currency) {
}
