package com.tms.gateway.modules.compliance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request to save step 4: Bank accounts (replaces all existing accounts).
 */
public record SaveBankAccountsRequest(
        @JsonProperty("bank_accounts") @NotEmpty(message = "At least one bank account is required") @Valid List<SaveBankAccountRequest> bankAccounts) {
}
