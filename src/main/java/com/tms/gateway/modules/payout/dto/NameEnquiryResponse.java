package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for bank account name enquiry.
 */
public record NameEnquiryResponse(@JsonProperty("account_name") String accountName,

        @JsonProperty("account_number") String accountNumber,

        @JsonProperty("bank_code") String bankCode,

        @JsonProperty("bank_name") String bankName) {

    /**
     * Create a response from the raw transaction service data.
     */
    public static NameEnquiryResponse fromMap(java.util.Map<String, Object> data) {
        return new NameEnquiryResponse(getString(data, "account_name"), getString(data, "account_number"),
                getString(data, "bank_code"), getString(data, "bank_name"));
    }

    private static String getString(java.util.Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
}
