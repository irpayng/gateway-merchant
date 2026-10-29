package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Nullable fields support partial drafts. Omitted scalar values retain their
 * saved value.
 */
public record SaveBankAccountRequest(@JsonProperty("id") Long id, @JsonProperty("bank_code") String bankCode,
        @JsonProperty("bank_name") String bankName, @JsonProperty("account_number") String accountNumber,
        @JsonProperty("account_name") String accountName, @JsonProperty("is_primary") Boolean isPrimary,
        @JsonProperty("currency") String currency) {
}
