package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
/**
 * Supplied collection replaces the previous list; an empty list clears the
 * draft section.
 */
public record SaveBankAccountsRequest(
        @JsonProperty("bank_accounts") @NotNull @Valid List<@NotNull SaveBankAccountRequest> bankAccounts) {
}
