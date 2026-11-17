package com.tms.gateway.modules.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record PayoutDetailDto(@JsonProperty("payout_id") String payoutId,
        @JsonProperty("payout_amount") BigDecimal payoutAmount, @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("status") String status, @JsonProperty("status_display") String statusDisplay,
        @JsonProperty("payment_reference") String paymentReference,
        @JsonProperty("total_amount") BigDecimal totalAmount, @JsonProperty("payout_value") BigDecimal payoutValue,
        @JsonProperty("date_time_settled") Instant dateTimeSettled,
        @JsonProperty("receiving_bank") ReceivingBank receivingBank) {

    public record ReceivingBank(@JsonProperty("bank_name") String bankName,
            @JsonProperty("account_name") String accountName, @JsonProperty("account_number") String accountNumber) {
    }
}
